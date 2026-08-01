package app.zhanzhuang.timer.wear.sync

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SyncEnvelope
import app.zhanzhuang.timer.model.SyncPayload
import app.zhanzhuang.timer.model.SyncTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class WearSyncCoordinatorTest {
    @Test
    fun matchingAckClearsOnlyMatchingOutboxEventAndRevision() = runTest {
        val outbox = FakeOutbox()
        val completion = envelope("event-1", 4, SyncPayload.Completed(record(4)))
        outbox.pending += OutboxEntry("event-1", "session-1", 4, completion)

        WearSyncCoordinator(outbox, FakeTransport()).receiveCommand(envelope("event-1", 3, SyncPayload.Ack(acceptedRevision = 3)))
        assertEquals(1, outbox.pending.size)
        WearSyncCoordinator(outbox, FakeTransport()).receiveCommand(envelope("event-1", 4, SyncPayload.Ack(acceptedRevision = 4)))
        assertTrue(outbox.pending.isEmpty())
    }

    @Test
    fun resendKeepsOutboxWhenDisconnected() = runTest {
        val outbox = FakeOutbox()
        val transport = FakeTransport(completedResult = false)
        val coordinator = WearSyncCoordinator(outbox, transport)
        outbox.pending += OutboxEntry("event-1", "session-1", 4, envelope("event-1", 4, SyncPayload.Completed(record(4))))

        coordinator.resendCompleted()

        assertEquals(1, outbox.pending.size)
        assertEquals(1, transport.completedAttempts)
    }

    private fun envelope(eventId: String, revision: Long, payload: SyncPayload) = SyncEnvelope(
        eventId = eventId,
        sessionId = "session-1",
        revision = revision,
        sentAtEpochMillis = 1_000,
        payload = payload,
    )

    private fun record(revision: Long) = SessionRecord(
        id = "session-1",
        revision = revision,
        config = SessionConfig(),
        status = SessionStatus.COMPLETED,
        owner = SessionOwner.WEAR,
        startEpochMillis = 1_000,
        endEpochMillis = 2_000,
    )

    private class FakeTransport(private val completedResult: Boolean = true) : SyncTransport {
        var completedAttempts = 0
        override suspend fun sendMessage(envelope: SyncEnvelope): Boolean = true
        override suspend fun putCompleted(envelope: SyncEnvelope): Boolean {
            completedAttempts++
            return completedResult
        }
    }

    private class FakeOutbox : WearCompletionOutbox {
        val pending = mutableListOf<OutboxEntry>()
        override suspend fun pendingCompleted(): List<OutboxEntry> = pending.toList()
        override suspend fun acknowledge(eventId: String, revision: Long) {
            pending.removeAll { it.eventId == eventId && it.revision == revision }
        }
    }
}
