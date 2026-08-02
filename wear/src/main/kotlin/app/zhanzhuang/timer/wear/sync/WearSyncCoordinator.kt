package app.zhanzhuang.timer.wear.sync

import app.zhanzhuang.timer.domain.SyncConflictResolver
import app.zhanzhuang.timer.model.PROTOCOL_VERSION
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SessionRuntime
import app.zhanzhuang.timer.model.SyncEnvelope
import app.zhanzhuang.timer.model.SyncPayload
import app.zhanzhuang.timer.model.SyncTransport
import app.zhanzhuang.timer.wear.data.OutboxEntity
import app.zhanzhuang.timer.wear.data.WearSessionRepository
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OutboxEntry(
    val eventId: String,
    val sessionId: String,
    val revision: Long,
    val envelope: SyncEnvelope,
)

interface WearCompletionOutbox {
    suspend fun pendingCompleted(): List<OutboxEntry>
    suspend fun acknowledge(eventId: String, revision: Long)
}

interface WearSyncController {
    suspend fun startFromRemote(config: SessionConfig, sessionId: String, revision: Long): SessionRecord?
    suspend fun current(): SessionRecord?
    suspend fun session(sessionId: String): SessionRecord?
    suspend fun pauseFromRemote(sessionId: String): SessionRecord?
    suspend fun resumeFromRemote(sessionId: String): SessionRecord?
    suspend fun finishFromRemote(sessionId: String, cancelled: Boolean): SessionRecord?
    suspend fun mergeRemote(record: SessionRecord): SessionRecord
}

/** Wear-side command receiver and durable completion retransmitter. */
class WearSyncCoordinator(
    private val outbox: WearCompletionOutbox,
    private val transport: SyncTransport,
    private val controller: WearSyncController? = null,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val resolver: SyncConflictResolver = SyncConflictResolver(),
) {
    private val seenEventIds = LinkedHashSet<String>()
    private val mutex = Mutex()

    suspend fun receiveCommand(envelope: SyncEnvelope, sourceNodeId: String? = null): Boolean = mutex.withLock {
        if (!isValidUnseenEnvelope(envelope)) return false
        val handled = when (val payload = envelope.payload) {
            is SyncPayload.Ack -> acknowledge(envelope.eventId, envelope.sessionId, envelope.revision, payload.acceptedRevision)
            is SyncPayload.Start -> {
                // A matching durable ID is an idempotent replay, even when the
                // actor has already advanced its revision after the first try.
                val current = controller?.session(envelope.sessionId)
                when {
                    current?.owner == SessionOwner.WEAR && current.status in START_REPLAY_STATUSES -> sendState(current, sourceNodeId)
                    payload.expiresAtEpochMillis <= nowEpochMillis() -> false
                    else -> controller?.startFromRemote(payload.config, envelope.sessionId, envelope.revision)?.let { sendState(it, sourceNodeId) } == true
                }
            }
            SyncPayload.QueryState -> controller?.current()?.let { sendState(it, sourceNodeId) } == true
            SyncPayload.Pause -> if (rejectsLowerRevision(envelope, setOf(SessionStatus.PAUSED))) false else controller?.pauseFromRemote(envelope.sessionId)?.let { sendState(it, sourceNodeId) } == true
            SyncPayload.Resume -> if (rejectsLowerRevision(envelope, setOf(SessionStatus.RUNNING))) false else controller?.resumeFromRemote(envelope.sessionId)?.let { sendState(it, sourceNodeId) } == true
            is SyncPayload.Finish -> if (rejectsLowerRevision(envelope, TERMINAL_STATUSES)) false else controller?.finishFromRemote(envelope.sessionId, payload.cancelled)?.let { sendState(it, sourceNodeId) } == true
            is SyncPayload.CancelIfUnowned -> {
                val current = controller?.session(envelope.sessionId)
                when {
                    current == null -> false
                    current.status in TERMINAL_STATUSES -> current.revision > payload.ownerRevision && sendState(current, sourceNodeId)
                    current.revision <= payload.ownerRevision ->
                        controller.finishFromRemote(envelope.sessionId, cancelled = true)?.let { sendState(it, sourceNodeId) } == true
                    else -> false
                }
            }
            is SyncPayload.State -> mergeRemote(payload.record)
            is SyncPayload.Runtime -> false
            is SyncPayload.Completed -> mergeRemote(payload.record)
        }
        if (handled) rememberEvent(envelope.eventId)
        handled
    }

    suspend fun resendCompleted(): Boolean = mutex.withLock {
        outbox.pendingCompleted().all { entry -> transport.putCompleted(entry.envelope) }
    }

    /** Re-establishes the authoritative Wear-owned timer after a paired phone reconnects. */
    suspend fun publishCurrentState(nodeId: String): Boolean = mutex.withLock {
        val record = controller?.current()?.takeIf {
            it.owner == SessionOwner.WEAR && it.status in ACTIVE_STATUSES
        } ?: return@withLock false
        sendState(record, nodeId)
    }

    /** Best-effort immediate terminal state; completed DataItem outbox remains the durable fallback. */
    suspend fun publishState(record: SessionRecord): Boolean = mutex.withLock { sendState(record, sourceNodeId = null) }

    /** Sends a low-frequency FGS runtime update without mutating the durable session revision. */
    suspend fun publishRuntime(runtime: SessionRuntime): Boolean = mutex.withLock {
        transport.sendMessage(
            SyncEnvelope(
                eventId = UUID.randomUUID().toString(),
                sessionId = runtime.sessionId,
                revision = runtime.revision,
                sentAtEpochMillis = runtime.reportedAtEpochMillis,
                payload = SyncPayload.Runtime(runtime),
            ),
        )
    }

    private suspend fun acknowledge(eventId: String, sessionId: String, envelopeRevision: Long, acceptedRevision: Long): Boolean {
        val matching = outbox.pendingCompleted().any {
            it.eventId == eventId &&
                it.sessionId == sessionId &&
                it.revision == envelopeRevision &&
                it.revision == acceptedRevision
        }
        if (!matching) return false
        outbox.acknowledge(eventId, acceptedRevision)
        return true
    }

    private suspend fun mergeRemote(incoming: SessionRecord): Boolean {
        val activeController = controller ?: return false
        val winner = resolver.merge(activeController.session(incoming.id), incoming)
        if (winner != incoming) return false
        activeController.mergeRemote(winner)
        return true
    }

    private suspend fun sendState(record: SessionRecord, sourceNodeId: String?): Boolean = respond(
        SyncEnvelope(
            eventId = UUID.randomUUID().toString(),
            sessionId = record.id,
            revision = record.revision,
            sentAtEpochMillis = nowEpochMillis(),
            payload = SyncPayload.State(record.copy(heartRateSamples = emptyList())),
        ),
        sourceNodeId,
    )

    private suspend fun respond(envelope: SyncEnvelope, sourceNodeId: String?): Boolean =
        if (sourceNodeId == null) transport.sendMessage(envelope) else transport.sendMessageTo(envelope, sourceNodeId)

    private fun isValidUnseenEnvelope(envelope: SyncEnvelope): Boolean {
        if (envelope.protocolVersion != PROTOCOL_VERSION || envelope.eventId.isBlank()) return false
        return envelope.eventId !in seenEventIds
    }

    private fun rememberEvent(eventId: String) {
        seenEventIds += eventId
        while (seenEventIds.size > MAX_SEEN_EVENTS) seenEventIds.remove(seenEventIds.first())
    }

    private suspend fun rejectsLowerRevision(envelope: SyncEnvelope, idempotentStatuses: Set<SessionStatus>): Boolean =
        controller?.session(envelope.sessionId)?.let { envelope.revision < it.revision && it.status !in idempotentStatuses } ?: false

    private companion object {
        const val MAX_SEEN_EVENTS = 512
        val START_REPLAY_STATUSES = setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING, SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.INTERRUPTED)
        val ACTIVE_STATUSES = setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
        val TERMINAL_STATUSES = setOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.INTERRUPTED)
    }
}

/** Adapts the Task 4 outbox to typed protocol envelopes without exposing Room to the service. */
class WearRepositoryCompletionOutbox(private val repository: WearSessionRepository) : WearCompletionOutbox {
    override suspend fun pendingCompleted(): List<OutboxEntry> = repository.pendingOutbox().mapNotNull { entity ->
        entity.toOutboxEntryOrNull()
    }

    override suspend fun acknowledge(eventId: String, revision: Long) = repository.acknowledge(eventId, revision)
}

private fun OutboxEntity.toOutboxEntryOrNull(): OutboxEntry? = runCatching {
    val envelope = app.zhanzhuang.timer.model.SyncWireCodec.decodeCompletedFromOutbox(payload)
    require(envelope.eventId == eventId && envelope.sessionId == sessionId && envelope.revision == revision)
    require((envelope.payload as app.zhanzhuang.timer.model.SyncPayload.Completed).record.id == sessionId)
    require((envelope.payload as app.zhanzhuang.timer.model.SyncPayload.Completed).record.revision == revision)
    OutboxEntry(eventId, sessionId, revision, envelope)
}.getOrNull()
