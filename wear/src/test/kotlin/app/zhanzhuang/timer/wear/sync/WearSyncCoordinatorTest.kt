package app.zhanzhuang.timer.wear.sync

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SyncEnvelope
import app.zhanzhuang.timer.model.SyncPayload
import app.zhanzhuang.timer.model.SyncTransport
import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun ackWithWrongSessionOrEnvelopeRevisionCannotClearOutbox() = runTest {
        val outbox = FakeOutbox()
        outbox.pending += OutboxEntry("event-1", "session-1", 4, envelope("event-1", 4, SyncPayload.Completed(record(4))))

        WearSyncCoordinator(outbox, FakeTransport()).receiveCommand(
            envelope("event-1", 4, SyncPayload.Ack(4)).copy(sessionId = "other-session"),
        )
        assertEquals(1, outbox.pending.size)
        WearSyncCoordinator(outbox, FakeTransport()).receiveCommand(envelope("event-1", 3, SyncPayload.Ack(4)))
        assertEquals(1, outbox.pending.size)
    }

    @Test
    fun timedOutStartCanRetryWithTheSameEventAfterTheActorPersistsState() = runTest {
        val persisted = record(2).copy(status = SessionStatus.RUNNING)
        val controller = RetryableController()
        val transport = FakeTransport()
        val coordinator = WearSyncCoordinator(FakeOutbox(), transport, controller, nowEpochMillis = { 1_000 })
        val start = envelope(
            eventId = "retry-start",
            revision = 1,
            payload = SyncPayload.Start(SessionConfig(), expiresAtEpochMillis = 2_000),
        )

        assertFalse(coordinator.receiveCommand(start))
        controller.persistAfterTimeout(persisted)
        assertTrue(coordinator.receiveCommand(start))

        assertEquals(2, controller.startCalls)
        assertEquals(1, transport.messages.count { it.payload is SyncPayload.State })
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
        val messages = mutableListOf<SyncEnvelope>()
        override suspend fun sendMessage(envelope: SyncEnvelope): Boolean {
            messages += envelope
            return true
        }
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

    private class RetryableController : WearSyncController {
        var startCalls = 0
        private var durableRecord: SessionRecord? = null
        override suspend fun startFromRemote(config: SessionConfig, sessionId: String, revision: Long): SessionRecord? {
            startCalls += 1
            return durableRecord
        }
        fun persistAfterTimeout(record: SessionRecord) { durableRecord = record }
        override suspend fun current(): SessionRecord? = null
        override suspend fun session(sessionId: String): SessionRecord? = null
        override suspend fun pauseFromRemote(sessionId: String): SessionRecord? = null
        override suspend fun resumeFromRemote(sessionId: String): SessionRecord? = null
        override suspend fun finishFromRemote(sessionId: String, cancelled: Boolean): SessionRecord? = null
        override suspend fun mergeRemote(record: SessionRecord): SessionRecord = record
    }
}
