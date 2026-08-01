package app.zhanzhuang.timer.mobile.sync

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SyncEnvelope
import app.zhanzhuang.timer.model.SyncPayload
import app.zhanzhuang.timer.model.SyncTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

@OptIn(ExperimentalCoroutinesApi::class)
class MobileSyncCoordinatorTest {
    @Test
    fun fallbackOccursAfterAckThenQueryTimeout() = runTest {
        val transport = FakeTransport()
        val controller = FakeController()
        val coordinator = MobileSyncCoordinator(
            transport = transport,
            controller = controller,
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            nowEpochMillis = { testScheduler.currentTime },
        )

        coordinator.startFromMobile(SessionConfig())
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("START", "QUERY_STATE"), transport.sentPayloadNames)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(SessionOwner.MOBILE, controller.owner())
    }

    @Test
    fun duplicateAndExpiredStartAreSafelyRejected() = runTest {
        val controller = FakeController()
        val coordinator = MobileSyncCoordinator(
            transport = FakeTransport(),
            controller = controller,
            scope = this,
            nowEpochMillis = { 10_000L },
        )
        val expired = envelope(
            eventId = "old",
            revision = 1,
            payload = SyncPayload.Start(SessionConfig(), expiresAtEpochMillis = 9_999),
        )

        assertFalse(coordinator.receiveCommand(expired))
        assertFalse(coordinator.receiveCommand(expired))
        assertEquals(0, controller.remoteStarts)
    }

    @Test
    fun completedEventAcknowledgesItsExactRevision() = runTest {
        val transport = FakeTransport()
        val coordinator = MobileSyncCoordinator(transport, FakeController(), this, nowEpochMillis = { 10_000L })
        val completion = envelope("completion-1", 4, SyncPayload.Completed(record(SessionOwner.WEAR).copy(revision = 4, status = SessionStatus.COMPLETED)))

        assertTrue(coordinator.receiveCompleted(completion))
        assertEquals("ACK", transport.sentPayloadNames.single())
    }

    @Test
    fun alreadyPersistedCompletionIsAcknowledgedAfterProcessRestart() = runTest {
        val transport = FakeTransport()
        val controller = FakeController().also {
            it.mergeRemote(record(SessionOwner.WEAR).copy(revision = 4, status = SessionStatus.COMPLETED))
        }
        val coordinator = MobileSyncCoordinator(transport, controller, this, nowEpochMillis = { 10_000L })

        assertTrue(coordinator.receiveCompleted(envelope("completion-1", 4, SyncPayload.Completed(record(SessionOwner.WEAR).copy(revision = 4, status = SessionStatus.COMPLETED)))) )
        assertEquals("ACK", transport.sentPayloadNames.single())
    }

    @Test
    fun replayedCompletionAfterLostAckIsAcknowledgedWhenLocalHealthStateChanged() = runTest {
        val transport = FakeTransport()
        val controller = FakeController().also {
            it.mergeRemote(record(SessionOwner.WEAR).copy(revision = 4, status = SessionStatus.COMPLETED, healthConnectState = app.zhanzhuang.timer.model.SyncState.PENDING))
        }
        val coordinator = MobileSyncCoordinator(transport, controller, this, nowEpochMillis = { 10_000L })

        assertTrue(coordinator.receiveCompleted(envelope("completion-retry", 4, SyncPayload.Completed(record(SessionOwner.WEAR).copy(revision = 4, status = SessionStatus.COMPLETED)))) )
        assertEquals("ACK", transport.sentPayloadNames.single())
    }

    @Test
    fun sameCompletedEventRetriesAckAfterTransportFailure() = runTest {
        val transport = FakeTransport(ackResults = ArrayDeque(listOf(false, true)))
        val controller = FakeController()
        val coordinator = MobileSyncCoordinator(transport, controller, this, nowEpochMillis = { 10_000L })
        val completed = envelope("completion-retry", 4, SyncPayload.Completed(record(SessionOwner.WEAR).copy(revision = 4, status = SessionStatus.COMPLETED)))

        assertFalse(coordinator.receiveCompleted(completed))
        assertTrue(coordinator.receiveCompleted(completed))
        assertEquals(2, transport.sentPayloadNames.count { it == "ACK" })
    }

    @Test
    fun wrongSessionPauseCannotOperateCurrentSession() = runTest {
        val controller = FakeController()
        val coordinator = MobileSyncCoordinator(FakeTransport(), controller, this, nowEpochMillis = { 10_000L })

        assertFalse(coordinator.receiveCommand(SyncEnvelope(eventId = "wrong-pause", sessionId = "other", revision = 1, sentAtEpochMillis = 1, payload = SyncPayload.Pause)))
        assertEquals(SessionStatus.RUNNING, controller.current()!!.status)
    }

    @Test
    fun queryStateNeverTransfersHeartRateHistory() = runTest {
        val transport = FakeTransport()
        val controller = FakeController().also {
            it.mergeRemote(record(SessionOwner.WEAR).copy(heartRateSamples = (1..10_800).map { sample ->
                app.zhanzhuang.timer.model.HeartRateSample(sample.toLong(), 60.0, app.zhanzhuang.timer.model.SampleAccuracy.HIGH)
            }))
        }
        val coordinator = MobileSyncCoordinator(transport, controller, this, nowEpochMillis = { 10_000L })

        assertTrue(coordinator.receiveCommand(envelope("query", 1, SyncPayload.QueryState)))
        val state = transport.envelopes.single().payload as SyncPayload.State
        assertTrue(state.record.heartRateSamples.isEmpty())
    }

    @Test
    fun lowerRevisionStateCannotReplaceCurrentOwner() = runTest {
        val controller = FakeController().also {
            it.mergeRemote(record(SessionOwner.MOBILE).copy(revision = 5))
        }
        val coordinator = MobileSyncCoordinator(FakeTransport(), controller, this, nowEpochMillis = { 10_000L })

        assertFalse(coordinator.receiveCommand(envelope("delayed-state", 4, SyncPayload.State(record(SessionOwner.WEAR).copy(revision = 4)))))
        assertEquals(SessionOwner.MOBILE, controller.owner())
    }

    @Test
    fun wearStateBeforeOrAfterQueryCancelsMobileFallback() = runTest {
        val transport = FakeTransport()
        val controller = FakeController()
        val coordinator = MobileSyncCoordinator(transport, controller, TestScope(StandardTestDispatcher(testScheduler)), { testScheduler.currentTime })
        val provisional = coordinator.startFromMobile(SessionConfig())

        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(coordinator.receiveCommand(envelope("wear-state", 1, SyncPayload.State(provisional.copy(owner = SessionOwner.WEAR, status = SessionStatus.RUNNING)))))
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(SessionOwner.WEAR, controller.owner())
        assertEquals(2, transport.sentPayloadNames.size)
    }

    @Test
    fun concurrentDuplicateStartExecutesOnlyOnce() = runTest {
        val controller = FakeController()
        val coordinator = MobileSyncCoordinator(FakeTransport(), controller, this, nowEpochMillis = { 1_000L })
        val start = envelope("one-start", 1, SyncPayload.Start(SessionConfig(), expiresAtEpochMillis = 2_000L))

        listOf(async { coordinator.receiveCommand(start) }, async { coordinator.receiveCommand(start) }).awaitAll()

        assertEquals(1, controller.remoteStarts)
    }

    @Test
    fun failedStateResponseDoesNotConsumeTheRemoteCommandEvent() = runTest {
        val transport = FakeTransport(messageResults = ArrayDeque(listOf(false, true)))
        val controller = FakeController().also { it.seed(record(SessionOwner.MOBILE).copy(status = SessionStatus.STARTING)) }
        val coordinator = MobileSyncCoordinator(transport, controller, this, nowEpochMillis = { 1_000L })
        val start = envelope("retry-start", 1, SyncPayload.Start(SessionConfig(), expiresAtEpochMillis = 2_000L))

        assertFalse(coordinator.receiveCommand(start))
        assertTrue(coordinator.receiveCommand(start))

        assertEquals(1, controller.remoteStarts)
        assertEquals(2, transport.sentPayloadNames.count { it == "STATE" })
    }

    @Test
    fun oldRevisionRetriesResendTheDurableTargetStateWithoutRepeatingRemoteActions() = runTest {
        val transport = FakeTransport(messageResults = ArrayDeque(List(4) { listOf(false, true) }.flatten()))
        val controller = FakeController().also { it.seed(record(SessionOwner.MOBILE).copy(status = SessionStatus.STARTING)) }
        val coordinator = MobileSyncCoordinator(transport, controller, this, nowEpochMillis = { 1_000L })
        val start = envelope("start", 1, SyncPayload.Start(SessionConfig(), expiresAtEpochMillis = 2_000L))

        assertFalse(coordinator.receiveCommand(start))
        assertTrue(coordinator.receiveCommand(start))
        val pause = envelope("pause", 2, SyncPayload.Pause)
        assertFalse(coordinator.receiveCommand(pause))
        assertTrue(coordinator.receiveCommand(pause))
        val resume = envelope("resume", 3, SyncPayload.Resume)
        assertFalse(coordinator.receiveCommand(resume))
        assertTrue(coordinator.receiveCommand(resume))
        val finish = envelope("finish", 4, SyncPayload.Finish(cancelled = false))
        assertFalse(coordinator.receiveCommand(finish))
        assertTrue(coordinator.receiveCommand(finish))

        assertEquals(1, controller.remoteStarts)
        assertEquals(1, controller.pauses)
        assertEquals(1, controller.resumes)
        assertEquals(1, controller.finishes)
        assertEquals(5, controller.current()!!.revision)
        assertEquals(SessionStatus.COMPLETED, controller.current()!!.status)
        assertEquals(8, transport.sentPayloadNames.count { it == "STATE" })
    }

    @Test
    fun cancelIfUnownedRetriesCancelledStateWithoutFinishingTwice() = runTest {
        val transport = FakeTransport(messageResults = ArrayDeque(listOf(false, true)))
        val controller = FakeController().also { it.seed(record(SessionOwner.WEAR)) }
        val coordinator = MobileSyncCoordinator(transport, controller, this, nowEpochMillis = { 1_000L })
        val cancel = envelope("cancel", 1, SyncPayload.CancelIfUnowned(ownerRevision = 1))

        assertFalse(coordinator.receiveCommand(cancel))
        assertTrue(coordinator.receiveCommand(cancel))

        assertEquals(1, controller.finishes)
        assertEquals(SessionStatus.CANCELLED, controller.current()!!.status)
        assertEquals(2, controller.current()!!.revision)
        assertEquals(2, transport.sentPayloadNames.count { it == "STATE" })
    }

    @Test
    fun cancelIfUnownedRejectsWrongSessionAndNonterminalHigherRevision() = runTest {
        val transport = FakeTransport()
        val controller = FakeController().also { it.seed(record(SessionOwner.WEAR).copy(revision = 2)) }
        val coordinator = MobileSyncCoordinator(transport, controller, this, nowEpochMillis = { 1_000L })

        assertFalse(coordinator.receiveCommand(envelope("wrong", 1, SyncPayload.CancelIfUnowned(ownerRevision = 2)).copy(sessionId = "other")))
        assertFalse(coordinator.receiveCommand(envelope("higher", 1, SyncPayload.CancelIfUnowned(ownerRevision = 1))))

        assertEquals(0, controller.finishes)
        assertTrue(transport.envelopes.isEmpty())
    }

    private fun envelope(eventId: String, revision: Long, payload: SyncPayload) = SyncEnvelope(
        eventId = eventId,
        sessionId = "session-1",
        revision = revision,
        sentAtEpochMillis = 1_000,
        payload = payload,
    )

    private class FakeTransport(
        private val ackResults: ArrayDeque<Boolean> = ArrayDeque(),
        private val messageResults: ArrayDeque<Boolean> = ArrayDeque(),
    ) : SyncTransport {
        val sentPayloadNames = mutableListOf<String>()
        val envelopes = mutableListOf<SyncEnvelope>()

        override suspend fun sendMessage(envelope: SyncEnvelope): Boolean {
            sentPayloadNames += envelope.payload::class.simpleName!!.replace("QueryState", "QUERY_STATE").uppercase()
            envelopes += envelope
            return when {
                envelope.payload is SyncPayload.Ack && ackResults.isNotEmpty() -> ackResults.removeFirst()
                messageResults.isNotEmpty() -> messageResults.removeFirst()
                else -> true
            }
        }

        override suspend fun putCompleted(envelope: SyncEnvelope): Boolean = true
    }

    private class FakeController : MobileSyncController {
        private var record = record(owner = SessionOwner.MOBILE)
        var remoteStarts = 0
        var pauses = 0
        var resumes = 0
        var finishes = 0

        override suspend fun startFromRemote(config: SessionConfig, sessionId: String, revision: Long): SessionRecord {
            remoteStarts++
            return record.copy(config = config, id = sessionId, revision = record.revision + 1, owner = SessionOwner.WEAR, status = SessionStatus.RUNNING)
                .also { record = it }
        }

        override suspend fun startFromMobile(config: SessionConfig, sessionId: String, revision: Long): SessionRecord =
            record.copy(config = config, id = sessionId, revision = revision, owner = SessionOwner.MOBILE, status = SessionStatus.STARTING)
                .also { record = it }

        override suspend fun current(): SessionRecord? = record

        override suspend fun session(sessionId: String): SessionRecord? = record.takeIf { it.id == sessionId }

        override suspend fun pauseFromRemote(sessionId: String): SessionRecord? = record.takeIf { it.id == sessionId }?.copy(status = SessionStatus.PAUSED, revision = record.revision + 1)?.also {
            pauses++
            record = it
        }

        override suspend fun resumeFromRemote(sessionId: String): SessionRecord? = record.takeIf { it.id == sessionId }?.copy(status = SessionStatus.RUNNING, revision = record.revision + 1)?.also {
            resumes++
            record = it
        }

        override suspend fun finishFromRemote(sessionId: String, cancelled: Boolean): SessionRecord? = record.takeIf { it.id == sessionId }?.copy(
            status = if (cancelled) SessionStatus.CANCELLED else SessionStatus.COMPLETED,
            revision = record.revision + 1,
        )?.also {
            finishes++
            record = it
        }

        override suspend fun becomeMobileOwner(sessionId: String): SessionRecord? =
            record.takeIf { it.id == sessionId }?.copy(owner = SessionOwner.MOBILE, revision = record.revision + 1)
                ?.also { record = it }

        override suspend fun mergeRemote(record: SessionRecord): SessionRecord = record.also { this.record = it }

        fun owner(): SessionOwner = record.owner
        fun seed(record: SessionRecord) { this.record = record }
    }

    private companion object {
        fun record(owner: SessionOwner) = SessionRecord(
            id = "session-1",
            revision = 1,
            config = SessionConfig(),
            status = SessionStatus.RUNNING,
            owner = owner,
            startEpochMillis = 1_000,
        )
    }
}
