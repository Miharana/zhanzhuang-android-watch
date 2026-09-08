package app.zhanzhuang.timer.mobile.sync

import app.zhanzhuang.timer.domain.SyncConflictResolver
import app.zhanzhuang.timer.model.PROTOCOL_VERSION
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SyncEnvelope
import app.zhanzhuang.timer.model.SyncPayload
import app.zhanzhuang.timer.model.SyncTransport
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface MobileSyncController {
    suspend fun startFromMobile(config: SessionConfig, sessionId: String, revision: Long): SessionRecord
    suspend fun startFromRemote(config: SessionConfig, sessionId: String, revision: Long): SessionRecord
    suspend fun current(): SessionRecord?
    suspend fun session(sessionId: String): SessionRecord?
    suspend fun pauseFromRemote(sessionId: String): SessionRecord?
    suspend fun resumeFromRemote(sessionId: String): SessionRecord?
    suspend fun finishFromRemote(sessionId: String, cancelled: Boolean): SessionRecord?
    suspend fun becomeMobileOwner(sessionId: String): SessionRecord?
    /** Marks a reserved local start as interrupted when foreground promotion fails. */
    suspend fun abandon(sessionId: String): Boolean = false
    suspend fun mergeRemote(record: SessionRecord): SessionRecord
}

/**
 * Owns the phone-side handshake. A newly requested watch start gets five
 * seconds for an acknowledgement/state, then a QueryState, then the phone
 * safely keeps ownership after another five seconds.
 */
class MobileSyncCoordinator(
    private val transport: SyncTransport,
    private val controller: MobileSyncController,
    private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long,
    private val resolver: SyncConflictResolver = SyncConflictResolver(),
    private val runtimeSink: (app.zhanzhuang.timer.model.SessionRuntime) -> Unit = MobileWearRuntimeBridge::publish,
) {
    private val seenEventIds = LinkedHashSet<String>()
    private val fallbackJobs = mutableMapOf<String, Job>()
    /**
     * Room intentionally does not persist high-frequency Wear checkpoints.
     * Keep their revision here so a later remote control command is not
     * rejected as older than the watch's heart-rate/checkpoint update.
     */
    private val latestRuntimeRevisions = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    suspend fun startFromMobile(config: SessionConfig): SessionRecord = mutex.withLock {
        val sessionId = UUID.randomUUID().toString()
        val record = controller.startFromMobile(config, sessionId, revision = 1)
        val start = envelope(record, SyncPayload.Start(config, nowEpochMillis() + START_EXPIRY_MS))
        val delivered = try {
            transport.sendMessage(start)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (!delivered) {
            return@withLock fallbackToMobile(record)
        }
        fallbackJobs.remove(record.id)?.cancel()
        fallbackJobs[record.id] = scope.launch {
            delay(ACK_TIMEOUT_MS)
            scope.launch { runCatching { transport.sendMessage(envelope(record, SyncPayload.QueryState)) } }
            delay(QUERY_TIMEOUT_MS)
            controller.becomeMobileOwner(record.id)?.let { local ->
                scope.launch { runCatching { transport.sendMessage(envelope(local, SyncPayload.State(local))) } }
            }
        }
        return record
    }

    /**
     * Offline start path. It reserves the same durable session ID first, then
     * immediately hands that reservation to the phone foreground service.
     */
    suspend fun startOnMobile(config: SessionConfig): SessionRecord = mutex.withLock {
        val sessionId = UUID.randomUUID().toString()
        controller.startFromMobile(config, sessionId, revision = 1)
        return try {
            requireNotNull(controller.becomeMobileOwner(sessionId)) {
                "The locally reserved session was not available for phone ownership"
            }.also { started ->
                check(started.id == sessionId && started.owner == SessionOwner.MOBILE) {
                    "Phone ownership changed the reserved session identity"
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            controller.abandon(sessionId)
            throw error
        }
    }

    private suspend fun fallbackToMobile(record: SessionRecord): SessionRecord = try {
        requireNotNull(controller.becomeMobileOwner(record.id)) {
            "The local fallback session was not available for phone ownership"
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        controller.abandon(record.id)
        throw error
    }

    /**
     * A reachable companion has returned. Ask it for the durable state of an
     * already watch-owned session; this never starts a foreground service.
     *
     * The phone may have been closed when the watch began a session, so there
     * may be no local Wear record yet. In that case a neutral discovery
     * envelope lets the watch return its actual durable record.
     */
    suspend fun queryCurrentWearState(): Boolean = mutex.withLock {
        val current = controller.current()
            ?.takeIf { it.owner == SessionOwner.WEAR && it.status in ACTIVE_STATUSES }
            ?: SessionRecord(
                id = WATCH_STATE_DISCOVERY_SESSION_ID,
                revision = 0,
                status = SessionStatus.IDLE,
                owner = SessionOwner.MOBILE,
            )
        transport.sendMessage(envelope(current, SyncPayload.QueryState))
    }

    /** Sends a command to a watch-owned session without manufacturing a local timer state. */
    suspend fun requestPause(sessionId: String): Boolean = requestRemoteCommand(sessionId, SyncPayload.Pause)

    suspend fun requestResume(sessionId: String): Boolean = requestRemoteCommand(sessionId, SyncPayload.Resume)

    suspend fun requestFinish(sessionId: String, cancelled: Boolean): Boolean =
        requestRemoteCommand(sessionId, SyncPayload.Finish(cancelled))

    private suspend fun requestRemoteCommand(sessionId: String, payload: SyncPayload): Boolean = mutex.withLock {
        val current = controller.session(sessionId) ?: return false
        if (current.owner != SessionOwner.WEAR || current.status !in ACTIVE_STATUSES) return false
        val commandRevision = maxOf(current.revision, latestRuntimeRevisions[current.id] ?: current.revision)
        transport.sendMessage(envelope(current.copy(revision = commandRevision), payload))
    }

    suspend fun receiveCommand(envelope: SyncEnvelope, sourceNodeId: String? = null): Boolean = mutex.withLock {
        if (!isValidUnseenEnvelope(envelope)) return false
        val handled = when (val payload = envelope.payload) {
            is SyncPayload.Start -> {
                val current = controller.session(envelope.sessionId)
                when {
                    // A same-ID terminal record is proof this start has already
                    // been decided; acknowledge it without allowing a revival.
                    current?.status in TERMINAL_STATUSES -> current?.let {
                        respond(envelope(it, SyncPayload.State(it)), sourceNodeId)
                    } ?: false
                    current?.owner == SessionOwner.WEAR && current.status in ACTIVE_STATUSES ->
                        respond(envelope(current, SyncPayload.State(current)), sourceNodeId)
                    payload.expiresAtEpochMillis <= nowEpochMillis() -> false
                    isLowerRevision(envelope) -> false
                    else -> {
                        val started = controller.startFromRemote(payload.config, envelope.sessionId, envelope.revision)
                        respond(envelope(started, SyncPayload.State(started)), sourceNodeId)
                    }
                }
            }
            SyncPayload.QueryState -> controller.current()?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) } == true
            is SyncPayload.State -> mergeRemote(payload.record)
            is SyncPayload.Runtime -> receiveRuntime(payload.runtime)
            is SyncPayload.Completed -> processCompleted(envelope, payload, sourceNodeId)
            is SyncPayload.Ack -> true
            SyncPayload.Pause -> {
                val current = controller.session(envelope.sessionId)
                when {
                    current?.status == SessionStatus.PAUSED -> respond(envelope(current, SyncPayload.State(current)), sourceNodeId)
                    rejectsLowerRevision(envelope, setOf(SessionStatus.PAUSED)) -> false
                    else -> controller.pauseFromRemote(envelope.sessionId)?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) } == true
                }
            }
            SyncPayload.Resume -> {
                val current = controller.session(envelope.sessionId)
                when {
                    current?.status == SessionStatus.RUNNING -> respond(envelope(current, SyncPayload.State(current)), sourceNodeId)
                    rejectsLowerRevision(envelope, setOf(SessionStatus.RUNNING)) -> false
                    else -> controller.resumeFromRemote(envelope.sessionId)?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) } == true
                }
            }
            is SyncPayload.Finish -> {
                val expected = if (payload.cancelled) SessionStatus.CANCELLED else SessionStatus.COMPLETED
                val current = controller.session(envelope.sessionId)
                when {
                    current?.status == expected -> respond(envelope(current, SyncPayload.State(current)), sourceNodeId)
                    rejectsLowerRevision(envelope, setOf(expected)) -> false
                    else -> controller.finishFromRemote(envelope.sessionId, payload.cancelled)?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) } == true
                }
            }
            is SyncPayload.CancelIfUnowned -> {
                val current = controller.session(envelope.sessionId)
                when {
                    current == null -> false
                    current.status in TERMINAL_STATUSES -> current.revision > payload.ownerRevision &&
                        respond(envelope(current, SyncPayload.State(current)), sourceNodeId)
                    current.revision <= payload.ownerRevision ->
                        controller.finishFromRemote(envelope.sessionId, cancelled = true)?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) } == true
                    else -> false
                }
            }
        }
        if (handled) rememberEvent(envelope.eventId)
        handled
    }

    suspend fun receiveCompleted(envelope: SyncEnvelope, sourceNodeId: String? = null): Boolean = mutex.withLock {
        // A lost ACK must be recoverable even when this process has already
        // seen the event ID. Repository/revision merge is the durable guard.
        if (envelope.protocolVersion != PROTOCOL_VERSION || envelope.eventId.isBlank()) return false
        val completed = envelope.payload as? SyncPayload.Completed ?: return false
        return processCompleted(envelope, completed, sourceNodeId)
    }

    private suspend fun processCompleted(envelope: SyncEnvelope, completed: SyncPayload.Completed, sourceNodeId: String?): Boolean {
        val accepted = mergeRemote(completed.record) || controller.session(completed.record.id)?.let { existing ->
            existing.status == app.zhanzhuang.timer.model.SessionStatus.COMPLETED && existing.revision >= completed.record.revision
        } == true
        if (!accepted) return false
        return respond(
            envelope(
                completed.record,
                SyncPayload.Ack(acceptedRevision = completed.record.revision),
                eventId = envelope.eventId,
            ),
            sourceNodeId,
        )
    }

    private suspend fun receiveRuntime(runtime: app.zhanzhuang.timer.model.SessionRuntime): Boolean {
        val current = controller.session(runtime.sessionId) ?: return false
        if (current.owner != SessionOwner.WEAR || current.status in TERMINAL_STATUSES) return false
        if (runtime.revision < current.revision) return false
        latestRuntimeRevisions[runtime.sessionId] = maxOf(
            latestRuntimeRevisions[runtime.sessionId] ?: current.revision,
            runtime.revision,
        )
        runtimeSink(runtime)
        return true
    }

    private suspend fun respond(envelope: SyncEnvelope, sourceNodeId: String?): Boolean =
        if (sourceNodeId == null) transport.sendMessage(envelope) else transport.sendMessageTo(envelope, sourceNodeId)

    private suspend fun mergeRemote(incoming: SessionRecord): Boolean {
        val current = controller.session(incoming.id)
        val winner = resolver.merge(current, incoming)
        if (winner != incoming) return false
        controller.mergeRemote(winner)
        if (winner.owner != SessionOwner.WEAR || winner.status in TERMINAL_STATUSES) {
            latestRuntimeRevisions.remove(winner.id)
        }
        fallbackJobs.remove(incoming.id)?.cancel()
        return true
    }

    private fun isValidUnseenEnvelope(envelope: SyncEnvelope): Boolean {
        if (envelope.protocolVersion != PROTOCOL_VERSION || envelope.eventId.isBlank()) return false
        return envelope.eventId !in seenEventIds
    }

    private fun rememberEvent(eventId: String) {
        seenEventIds += eventId
        while (seenEventIds.size > MAX_SEEN_EVENTS) seenEventIds.remove(seenEventIds.first())
    }

    private suspend fun isLowerRevision(envelope: SyncEnvelope): Boolean =
        controller.session(envelope.sessionId)?.let { envelope.revision < it.revision } ?: false

    private suspend fun rejectsLowerRevision(envelope: SyncEnvelope, idempotentStatuses: Set<SessionStatus>): Boolean =
        controller.session(envelope.sessionId)?.let { envelope.revision < it.revision && it.status !in idempotentStatuses } ?: false

    private fun envelope(record: SessionRecord, payload: SyncPayload, eventId: String = UUID.randomUUID().toString()) = SyncEnvelope(
        eventId = eventId,
        sessionId = record.id,
        revision = record.revision,
        sentAtEpochMillis = nowEpochMillis(),
        payload = if (payload is SyncPayload.State) payload.copy(record = payload.record.copy(heartRateSamples = emptyList())) else payload,
    )

    private companion object {
        const val ACK_TIMEOUT_MS = 5_000L
        const val QUERY_TIMEOUT_MS = 5_000L
        const val START_EXPIRY_MS = ACK_TIMEOUT_MS + QUERY_TIMEOUT_MS
        const val MAX_SEEN_EVENTS = 512
        const val WATCH_STATE_DISCOVERY_SESSION_ID = "wear-state-discovery"
        val ACTIVE_STATUSES = setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
        val TERMINAL_STATUSES = setOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.INTERRUPTED)
    }
}
