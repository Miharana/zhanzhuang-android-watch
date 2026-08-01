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

interface MobileSyncController {
    suspend fun startFromMobile(config: SessionConfig, sessionId: String, revision: Long): SessionRecord
    suspend fun startFromRemote(config: SessionConfig, sessionId: String, revision: Long): SessionRecord
    suspend fun current(): SessionRecord?
    suspend fun session(sessionId: String): SessionRecord?
    suspend fun pauseFromRemote(): SessionRecord?
    suspend fun resumeFromRemote(): SessionRecord?
    suspend fun finishFromRemote(cancelled: Boolean): SessionRecord?
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

    suspend fun startFromMobile(config: SessionConfig): SessionRecord {
        val sessionId = UUID.randomUUID().toString()
        val record = controller.startFromMobile(config, sessionId, revision = 1)
        val start = envelope(record, SyncPayload.Start(config, nowEpochMillis() + START_EXPIRY_MS))
        transport.sendMessage(start)
        fallbackJobs.remove(record.id)?.cancel()
        fallbackJobs[record.id] = scope.launch {
            delay(ACK_TIMEOUT_MS)
            transport.sendMessage(envelope(record, SyncPayload.QueryState))
            delay(QUERY_TIMEOUT_MS)
            controller.becomeMobileOwner(record.id)?.let { local ->
                transport.sendMessage(envelope(local, SyncPayload.State(local)))
            }
        }
        return record
    }

    suspend fun receiveCommand(envelope: SyncEnvelope): Boolean {
        if (!acceptEnvelope(envelope)) return false
        return when (val payload = envelope.payload) {
            is SyncPayload.Start -> {
                if (payload.expiresAtEpochMillis <= nowEpochMillis()) return false
                if (isLowerRevision(envelope)) return false
                val started = controller.startFromRemote(payload.config, envelope.sessionId, envelope.revision)
                transport.sendMessage(envelope(started, SyncPayload.State(started)))
                true
            }
            SyncPayload.QueryState -> {
                controller.current()?.let { transport.sendMessage(envelope(it, SyncPayload.State(it))) }
                true
            }
            is SyncPayload.State -> mergeRemote(payload.record)
            is SyncPayload.Completed -> processCompleted(envelope, payload)
            is SyncPayload.Ack -> true
            SyncPayload.Pause -> if (isLowerRevision(envelope)) false else controller.pauseFromRemote()?.let { transport.sendMessage(envelope(it, SyncPayload.State(it))) } != null
            SyncPayload.Resume -> if (isLowerRevision(envelope)) false else controller.resumeFromRemote()?.let { transport.sendMessage(envelope(it, SyncPayload.State(it))) } != null
            is SyncPayload.Finish -> if (isLowerRevision(envelope)) false else controller.finishFromRemote(payload.cancelled)?.let { transport.sendMessage(envelope(it, SyncPayload.State(it))) } != null
            is SyncPayload.CancelIfUnowned -> {
                val current = controller.session(envelope.sessionId) ?: return false
                if (current.revision <= payload.ownerRevision) controller.finishFromRemote(cancelled = true)?.let { transport.sendMessage(envelope(it, SyncPayload.State(it))) } != null else false
            }
        }
    }

    suspend fun receiveCompleted(envelope: SyncEnvelope): Boolean {
        if (!acceptEnvelope(envelope)) return false
        val completed = envelope.payload as? SyncPayload.Completed ?: return false
        return processCompleted(envelope, completed)
    }

    private suspend fun processCompleted(envelope: SyncEnvelope, completed: SyncPayload.Completed): Boolean {
        if (!mergeRemote(completed.record)) return false
        transport.sendMessage(
            envelope(
                completed.record,
                SyncPayload.Ack(acceptedRevision = completed.record.revision),
                eventId = envelope.eventId,
            ),
        )
        return true
    }

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
        payload = payload,
    )

    private companion object {
        const val ACK_TIMEOUT_MS = 5_000L
        const val QUERY_TIMEOUT_MS = 5_000L
        const val START_EXPIRY_MS = ACK_TIMEOUT_MS + QUERY_TIMEOUT_MS
        const val MAX_SEEN_EVENTS = 512
    }
}
