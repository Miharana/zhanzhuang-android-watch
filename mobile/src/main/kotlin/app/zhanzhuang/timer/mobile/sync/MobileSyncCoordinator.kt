package app.zhanzhuang.timer.mobile.sync

import app.zhanzhuang.timer.domain.SyncConflictResolver
import app.zhanzhuang.timer.model.PROTOCOL_VERSION
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SyncEnvelope
import app.zhanzhuang.timer.model.SyncPayload
import app.zhanzhuang.timer.model.SyncTransport
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
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
) {
    private val seenEventIds = LinkedHashSet<String>()
    private val fallbackJobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()

    suspend fun startFromMobile(config: SessionConfig): SessionRecord = mutex.withLock {
        val sessionId = UUID.randomUUID().toString()
        val record = controller.startFromMobile(config, sessionId, revision = 1)
        val start = envelope(record, SyncPayload.Start(config, nowEpochMillis() + START_EXPIRY_MS))
        scope.launch { runCatching { transport.sendMessage(start) } }
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

    suspend fun receiveCommand(envelope: SyncEnvelope, sourceNodeId: String? = null): Boolean = mutex.withLock {
        if (!acceptEnvelope(envelope)) return false
        return when (val payload = envelope.payload) {
            is SyncPayload.Start -> {
                if (payload.expiresAtEpochMillis <= nowEpochMillis()) return false
                if (isLowerRevision(envelope)) return false
                val started = controller.startFromRemote(payload.config, envelope.sessionId, envelope.revision)
                respond(envelope(started, SyncPayload.State(started)), sourceNodeId)
                true
            }
            SyncPayload.QueryState -> {
                controller.current()?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) }
                true
            }
            is SyncPayload.State -> mergeRemote(payload.record)
            is SyncPayload.Completed -> processCompleted(envelope, payload, sourceNodeId)
            is SyncPayload.Ack -> true
            SyncPayload.Pause -> if (isLowerRevision(envelope)) false else controller.pauseFromRemote(envelope.sessionId)?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) } != null
            SyncPayload.Resume -> if (isLowerRevision(envelope)) false else controller.resumeFromRemote(envelope.sessionId)?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) } != null
            is SyncPayload.Finish -> if (isLowerRevision(envelope)) false else controller.finishFromRemote(envelope.sessionId, payload.cancelled)?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) } != null
            is SyncPayload.CancelIfUnowned -> {
                val current = controller.session(envelope.sessionId) ?: return false
                if (current.revision <= payload.ownerRevision) controller.finishFromRemote(envelope.sessionId, cancelled = true)?.let { respond(envelope(it, SyncPayload.State(it)), sourceNodeId) } != null else false
            }
        }
    }

    suspend fun receiveCompleted(envelope: SyncEnvelope, sourceNodeId: String? = null): Boolean = mutex.withLock {
        if (!acceptEnvelope(envelope)) return false
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

    private suspend fun respond(envelope: SyncEnvelope, sourceNodeId: String?): Boolean =
        if (sourceNodeId == null) transport.sendMessage(envelope) else transport.sendMessageTo(envelope, sourceNodeId)

    private suspend fun mergeRemote(incoming: SessionRecord): Boolean {
        val current = controller.session(incoming.id)
        val winner = resolver.merge(current, incoming)
        if (winner != incoming) return false
        controller.mergeRemote(winner)
        fallbackJobs.remove(incoming.id)?.cancel()
        return true
    }

    private fun acceptEnvelope(envelope: SyncEnvelope): Boolean {
        if (envelope.protocolVersion != PROTOCOL_VERSION || envelope.eventId.isBlank()) return false
        if (!seenEventIds.add(envelope.eventId)) return false
        while (seenEventIds.size > MAX_SEEN_EVENTS) seenEventIds.remove(seenEventIds.first())
        return true
    }

    private suspend fun isLowerRevision(envelope: SyncEnvelope): Boolean =
        controller.session(envelope.sessionId)?.let { envelope.revision < it.revision } ?: false

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
    }
}
