package app.zhanzhuang.timer.wear.session

import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SampleAccuracy
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.health.DurationOnlyReason
import app.zhanzhuang.timer.wear.health.WearHealthClient
import app.zhanzhuang.timer.wear.health.WearHealthStartResult
import app.zhanzhuang.timer.wear.health.WearHealthUpdate
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearSessionControllerTest {
    @Test
    fun startIntentRequiresAnExplicitUserActionAndCarriesOnlyValidatedConfig() {
        val intent = WearSessionService.startIntent(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            config = SessionConfig(15, 5),
            sessionId = "s1",
        )

        assertEquals(WearSessionService.ACTION_START, intent.action)
        assertEquals("s1", intent.getStringExtra(WearSessionService.EXTRA_SESSION_ID))
        assertEquals(15, intent.getIntExtra(WearSessionService.EXTRA_DURATION_MINUTES, -1))
        assertEquals(5, intent.getIntExtra(WearSessionService.EXTRA_INTERVAL_MINUTES, -1))
        assertEquals(null, WearSessionService.commandFrom(Intent()))
    }

    @Test
    fun completionPersistsBeforeExternalEnd() = runTest {
        val events = mutableListOf<String>()
        val repository = RecordingStore(events)
        val clock = AdjustableClock()
        val controller = WearSessionControllerImpl(
            records = repository,
            snapshots = RecordingSnapshots(),
            health = RecordingHealth(events),
            clock = clock,
            haptics = RecordingHaptics(),
        )

        controller.start(SessionConfig(15, 5), "s1")
        events.clear()
        clock.advanceBy(900_000)
        controller.tick()

        assertEquals(
            listOf("persist:COMPLETING", "health:end", "persist:COMPLETED", "outbox:s1"),
            events,
        )
    }

    @Test
    fun changedBootMarksInterruptedWithoutInventingSamples() = runTest {
        val repository = RecordingStore()
        val snapshots = RecordingSnapshots()
        snapshots.save(
            WearSessionSnapshot(
                record = record(status = SessionStatus.RUNNING, samples = samples()),
                bootId = "old",
                runningSinceBootMs = 100,
                nextReminderAtActiveMs = 300_000,
            ),
        )
        val controller = WearSessionControllerImpl(
            records = repository,
            snapshots = snapshots,
            health = RecordingHealth(),
            clock = AdjustableClock(),
            haptics = RecordingHaptics(),
        )

        controller.recover(currentBootId = "new")

        assertEquals(SessionStatus.INTERRUPTED, repository.latest()!!.status)
        assertEquals(samples(), repository.latest()!!.heartRateSamples)
        assertFalse(repository.latest()!!.heartRateSamples.any { it.epochMillis > 1_002 })
    }

    @Test
    fun durationOnlyDoesNotEndAnotherAppsExercise() = runTest {
        val health = RecordingHealth(startResult = WearHealthStartResult.DurationOnly(DurationOnlyReason.OTHER_APP_EXERCISE))
        val controller = WearSessionControllerImpl(
            records = RecordingStore(),
            snapshots = RecordingSnapshots(),
            health = health,
            clock = AdjustableClock(),
            haptics = RecordingHaptics(),
        )

        controller.start(SessionConfig(15, 5), "s1")
        controller.finish()

        assertFalse(health.ended)
    }

    @Test
    fun delayedTickEmitsAtMostOneIntervalCue() = runTest {
        val haptics = RecordingHaptics()
        val clock = AdjustableClock()
        val controller = WearSessionControllerImpl(
            records = RecordingStore(),
            snapshots = RecordingSnapshots(),
            health = RecordingHealth(),
            clock = clock,
            haptics = haptics,
        )

        controller.start(SessionConfig(15, 5), "s1")
        clock.advanceBy(899_000)
        controller.tick()

        assertEquals(1, haptics.cues.count { it == Cue.INTERVAL })
        assertTrue(haptics.cues.contains(Cue.COMPLETE).not())
    }

    @Test
    fun unchangedTerminalRecoveryDoesNotBumpRevision() = runTest {
        val store = RecordingStore()
        val snapshots = RecordingSnapshots().also {
            it.save(WearSessionSnapshot(record(SessionStatus.COMPLETED).copy(revision = 7), "boot"))
        }
        val controller = controller(store, snapshots)

        controller.recover("boot")

        assertEquals(7, controller.state.value.record!!.revision)
        assertEquals(7, store.latest()!!.revision)
    }

    @Test
    fun startingRecoverySafelyCompletesStart() = runTest {
        val health = RecordingHealth()
        val controller = WearSessionControllerImpl(
            RecordingStore(),
            RecordingSnapshots().also { it.save(WearSessionSnapshot(record(SessionStatus.STARTING), "boot")) },
            health,
            AdjustableClock(),
            RecordingHaptics(),
        )

        controller.recover("boot")

        assertEquals(SessionStatus.RUNNING, controller.state.value.record!!.status)
        assertEquals(1, health.starts)
    }

    @Test
    fun completingRecoveryEndsOwnedExerciseThenTerminalizes() = runTest {
        val health = RecordingHealth()
        val controller = WearSessionControllerImpl(
            RecordingStore(),
            RecordingSnapshots().also {
                it.save(WearSessionSnapshot(record(SessionStatus.COMPLETING), "boot", healthExerciseOwned = true))
            },
            health,
            AdjustableClock(),
            RecordingHaptics(),
        )

        controller.recover("boot")

        assertTrue(health.ended)
        assertEquals(1, health.starts)
        assertEquals(SessionStatus.COMPLETED, controller.state.value.record!!.status)
    }

    @Test
    fun completingCancellationRecoveryRemainsCancelled() = runTest {
        val store = RecordingStore()
        val controller = WearSessionControllerImpl(
            store,
            RecordingSnapshots().also {
                it.save(
                    WearSessionSnapshot(
                        record(SessionStatus.COMPLETING), "boot",
                        pendingOperation = WearExternalOperation.END,
                        intendedTerminalStatus = SessionStatus.CANCELLED,
                    ),
                )
            },
            RecordingHealth(), AdjustableClock(), RecordingHaptics(),
        )

        controller.recover("boot")

        assertEquals(SessionStatus.CANCELLED, store.latest()!!.status)
    }

    @Test
    fun pendingPauseRecoveryKeepsFrozenActiveDurationAndPauseAnchor() = runTest {
        val clock = AdjustableClock().also { it.advanceBy(1_000) }
        val store = RecordingStore()
        val controller = WearSessionControllerImpl(
            store,
            RecordingSnapshots().also {
                it.save(
                    WearSessionSnapshot(
                        record(SessionStatus.RUNNING).copy(activeDurationMs = 500), "boot",
                        pendingOperation = WearExternalOperation.PAUSE,
                        pausedSinceBootMs = 500,
                    ),
                )
            },
            RecordingHealth(), clock, RecordingHaptics(),
        )

        controller.recover("boot")

        assertEquals(SessionStatus.PAUSED, store.latest()!!.status)
        assertEquals(500, store.latest()!!.activeDurationMs)
    }

    @Test
    fun pausedRecoveryPreservesOriginalPauseAnchorUntilResume() = runTest {
        val clock = AdjustableClock().also { it.advanceBy(1_000) }
        val store = RecordingStore()
        val controller = WearSessionControllerImpl(
            store,
            RecordingSnapshots().also {
                it.save(WearSessionSnapshot(record(SessionStatus.PAUSED), "boot", pausedSinceBootMs = 500, healthExerciseOwned = true))
            },
            RecordingHealth(), clock, RecordingHaptics(),
        )

        controller.recover("boot")
        clock.advanceBy(500)
        controller.resume()

        assertEquals(1_000, store.latest()!!.pausedDurationMs)
    }

    @Test
    fun recoveryMergesDurableHeartRateHistoryWhenSnapshotOmitsIt() = runTest {
        val store = RecordingStore().also { it.seed(record(SessionStatus.RUNNING, samples())) }
        val controller = WearSessionControllerImpl(
            store,
            RecordingSnapshots().also { it.save(WearSessionSnapshot(record(SessionStatus.RUNNING), "boot", runningSinceBootMs = 0, heartRateAcceptFromBootMs = 0)) },
            RecordingHealth(), AdjustableClock(), RecordingHaptics(),
        )

        controller.recover("boot")

        assertEquals(samples(), controller.state.value.record!!.heartRateSamples)
    }

    @Test
    fun heartRateUsesEventMonotonicTimestampAndOnlyRunningOwnedSession() = runTest {
        val clock = AdjustableClock().also { it.advanceBy(500) }
        val controller = WearSessionControllerImpl(
            RecordingStore(), RecordingSnapshots(), RecordingHealth(), clock, RecordingHaptics(),
        )
        controller.start(SessionConfig(15, 5), "s1")
        clock.advanceBy(25)
        controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(63.0, timeSinceBootMs = 525))

        assertEquals(1_525, controller.state.value.record!!.heartRateSamples.single().epochMillis)
        controller.pause()
        controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(64.0, timeSinceBootMs = 200))
        assertEquals(1, controller.state.value.record!!.heartRateSamples.size)
    }

    @Test
    fun heartRateBatchesContainOnlyNewRowsAndKeepTheHistoryUnion() = runTest {
        val store = RecordingStore()
        val clock = AdjustableClock()
        val controller = WearSessionControllerImpl(
            store, RecordingSnapshots(), RecordingHealth(), clock, RecordingHaptics(), bootId = { "boot" },
        )
        controller.start(SessionConfig(15, 5), "s1")
        val readsBeforeBatches = store.getCalls

        repeat(40) { offset ->
            clock.advanceBy(1)
            controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(60.0 + offset, clock.elapsedRealtimeMs()))
        }

        assertEquals(listOf(20, 20), store.checkpointBatches.map(List<HeartRateSample>::size))
        assertEquals(40, store.latest()!!.heartRateSamples.distinctBy(HeartRateSample::epochMillis).size)
        assertEquals(40, store.checkpointBatches.flatten().distinctBy(HeartRateSample::epochMillis).size)
        assertEquals(readsBeforeBatches, store.getCalls)
    }

    @Test
    fun pauseFlushesAnUnderfullHeartRateDeltaExactlyOnce() = runTest {
        val store = RecordingStore()
        val clock = AdjustableClock()
        val controller = WearSessionControllerImpl(
            store, RecordingSnapshots(), RecordingHealth(), clock, RecordingHaptics(), bootId = { "boot" },
        )
        controller.start(SessionConfig(15, 5), "s1")
        repeat(3) { offset ->
            clock.advanceBy(1)
            controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(60.0 + offset, clock.elapsedRealtimeMs()))
        }

        controller.pause()

        assertEquals(listOf(3), store.checkpointBatches.map(List<HeartRateSample>::size))
        assertEquals(3, store.latest()!!.heartRateSamples.distinctBy(HeartRateSample::epochMillis).size)
    }

    @Test
    fun statusFlushesAnUnderfullHeartRateDeltaExactlyOnce() = runTest {
        val store = RecordingStore()
        val snapshots = RecordingSnapshots()
        val clock = AdjustableClock()
        val controller = WearSessionControllerImpl(store, snapshots, RecordingHealth(), clock, RecordingHaptics(), bootId = { "boot" })
        controller.start(SessionConfig(15, 5), "s1")
        repeat(3) { offset ->
            clock.advanceBy(1)
            controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(60.0 + offset, clock.elapsedRealtimeMs()))
        }

        WearSessionCommandProcessor(controller).process(WearSessionService.Command.Status, "boot")

        assertEquals(listOf(3), store.checkpointBatches.map(List<HeartRateSample>::size))
    }

    @Test
    fun finishFlushesAnUnderfullHeartRateDeltaExactlyOnce() = runTest {
        val store = RecordingStore()
        val clock = AdjustableClock()
        val controller = WearSessionControllerImpl(store, RecordingSnapshots(), RecordingHealth(), clock, RecordingHaptics(), bootId = { "boot" })
        controller.start(SessionConfig(15, 5), "s1")
        repeat(3) { offset ->
            clock.advanceBy(1)
            controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(60.0 + offset, clock.elapsedRealtimeMs()))
        }

        controller.finish()

        assertEquals(listOf(3), store.checkpointBatches.map(List<HeartRateSample>::size))
    }

    @Test
    fun thirtySecondBatchesAppendOnlyNewRows() = runTest {
        val store = RecordingStore()
        val clock = AdjustableClock()
        val controller = WearSessionControllerImpl(store, RecordingSnapshots(), RecordingHealth(), clock, RecordingHaptics(), bootId = { "boot" })
        controller.start(SessionConfig(15, 5), "s1")
        repeat(2) { offset ->
            clock.advanceBy(30_000)
            controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(60.0 + offset, clock.elapsedRealtimeMs()))
        }

        assertEquals(listOf(1, 1), store.checkpointBatches.map(List<HeartRateSample>::size))
        assertEquals(2, store.latest()!!.heartRateSamples.size)
    }

    @Test
    fun snapshotCarriesOnlyTheBoundedUnflushedTail() = runTest {
        val snapshots = RecordingSnapshots()
        val clock = AdjustableClock()
        val store = RecordingStore()
        val controller = WearSessionControllerImpl(
            store, snapshots, RecordingHealth(), clock, RecordingHaptics(), bootId = { "boot" },
        )
        controller.start(SessionConfig(15, 5), "s1")
        store.failNextCheckpoint = true
        repeat(20) { offset ->
            clock.advanceBy(1)
            controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(60.0 + offset, clock.elapsedRealtimeMs()))
        }

        assertTrue(snapshots.snapshot!!.record.heartRateSamples.isEmpty())
        assertEquals(20, snapshots.snapshot!!.pendingHeartRateSamples.size)
    }

    @Test
    fun underfullHeartRateCallbacksDoNotWriteSnapshots() = runTest {
        val snapshots = RecordingSnapshots()
        val clock = AdjustableClock()
        val controller = WearSessionControllerImpl(
            RecordingStore(), snapshots, RecordingHealth(), clock, RecordingHaptics(), bootId = { "boot" },
        )
        controller.start(SessionConfig(15, 5), "s1")
        val writesAfterStart = snapshots.saveCount

        repeat(19) { offset ->
            clock.advanceBy(1)
            controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(60.0 + offset, clock.elapsedRealtimeMs()))
        }

        assertEquals(writesAfterStart, snapshots.saveCount)
    }

    @Test
    fun failedHealthPersistenceRetainsTheDeltaForTheNextUpdate() = runTest {
        val store = RecordingStore()
        val clock = AdjustableClock()
        val controller = WearSessionControllerImpl(
            store, RecordingSnapshots(), RecordingHealth(), clock, RecordingHaptics(), bootId = { "boot" },
        )
        controller.start(SessionConfig(15, 5), "s1")
        store.failNextCheckpoint = true
        repeat(20) { offset ->
            clock.advanceBy(1)
            controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(60.0 + offset, clock.elapsedRealtimeMs()))
        }
        clock.advanceBy(1)
        controller.acceptHealthUpdate(WearHealthUpdate.HeartRate(81.0, clock.elapsedRealtimeMs()))

        assertEquals(20, store.checkpointBatches.single().size)
    }

    @Test
    fun pauseAndResumeActionsAreParsed() {
        assertEquals(WearSessionService.Command.Pause, WearSessionService.commandFrom(Intent(WearSessionService.ACTION_PAUSE)))
        assertEquals(WearSessionService.Command.Resume, WearSessionService.commandFrom(Intent(WearSessionService.ACTION_RESUME)))
    }

    @Test
    fun commandActorStopsStatusWhenNoDurableSessionExists() = runTest {
        val result = WearSessionCommandProcessor(controller(RecordingStore(), RecordingSnapshots()))
            .process(WearSessionService.Command.Status, "boot")

        assertEquals(ServiceCommandResult.STOP, result)
    }

    @Test
    fun commandActorRecoversBeforeRejectingASecondStart() = runTest {
        val snapshots = RecordingSnapshots().also {
            it.save(WearSessionSnapshot(record(SessionStatus.RUNNING), "boot", runningSinceBootMs = 0))
        }
        val processor = WearSessionCommandProcessor(controller(RecordingStore(), snapshots))

        val result = processor.process(WearSessionService.Command.Start(SessionConfig(15, 5), "new"), "boot")

        assertEquals(ServiceCommandResult.ACTIVE, result)
    }

    @Test
    fun commandActorRejectsStartForAnotherSessionWithoutChangingTheActiveSession() = runTest {
        val store = RecordingStore()
        val snapshots = RecordingSnapshots().also {
            it.save(WearSessionSnapshot(record(SessionStatus.RUNNING), "boot", runningSinceBootMs = 0))
        }
        val processor = WearSessionCommandProcessor(controller(store, snapshots))

        val result = processor.process(
            WearSessionService.Command.Start(SessionConfig(15, 5), "session-a"),
            bootId = "boot",
            expectedSessionId = "session-a",
        )

        assertEquals(ServiceCommandResult.REJECTED, result)
        assertEquals("s1", store.latest()?.id)
        assertEquals(SessionStatus.RUNNING, store.latest()?.status)
    }

    @Test
    fun commandActorRecoversBeforeFinish() = runTest {
        val store = RecordingStore()
        val snapshots = RecordingSnapshots().also {
            it.save(WearSessionSnapshot(record(SessionStatus.RUNNING), "boot", runningSinceBootMs = 0))
        }

        val result = WearSessionCommandProcessor(controller(store, snapshots))
            .process(WearSessionService.Command.Finish, "boot")

        assertEquals(ServiceCommandResult.STOP, result)
        assertEquals(SessionStatus.COMPLETED, store.latest()!!.status)
    }

    @Test
    fun terminalRecoveryRejectsDuplicateStartForTheSameSessionId() = runTest {
        val store = RecordingStore()
        val terminal = record(SessionStatus.COMPLETED)
        val snapshots = RecordingSnapshots().also { it.save(WearSessionSnapshot(terminal, "boot")) }
        val health = RecordingHealth()
        val controller = WearSessionControllerImpl(
            store, snapshots, health, AdjustableClock(), RecordingHaptics(), bootId = { "boot" },
        )

        val result = WearSessionCommandProcessor(controller).process(
            WearSessionService.Command.Start(SessionConfig(15, 5), terminal.id),
            bootId = "boot",
            expectedSessionId = terminal.id,
        )

        assertEquals(ServiceCommandResult.REJECTED, result)
        assertEquals(0, health.starts)
        assertEquals(SessionStatus.COMPLETED, controller.state.value.record!!.status)
        assertEquals(terminal.id, controller.state.value.record!!.id)
    }

    @Test
    fun completedTransactionSchedulesDurableOutboxDelivery() = runTest {
        var schedules = 0
        val controller = WearSessionControllerImpl(
            RecordingStore(), RecordingSnapshots(), RecordingHealth(), AdjustableClock(), RecordingHaptics(),
            bootId = { "boot" }, completionSyncScheduler = { schedules += 1 },
        )

        controller.start(SessionConfig(15, 5), "s1")
        controller.finish()

        assertEquals(1, schedules)
    }

    private fun record(
        status: SessionStatus,
        samples: List<HeartRateSample> = emptyList(),
    ) = SessionRecord(
        id = "s1",
        revision = 1,
        config = SessionConfig(15, 5),
        status = status,
        owner = SessionOwner.WEAR,
        startEpochMillis = 1_000,
        heartRateSamples = samples,
    )

    private fun samples() = listOf(
        HeartRateSample(1_001, 60.0, SampleAccuracy.HIGH),
        HeartRateSample(1_002, 61.0, SampleAccuracy.HIGH),
    )

    private fun controller(store: RecordingStore, snapshots: RecordingSnapshots) = WearSessionControllerImpl(
        store, snapshots, RecordingHealth(), AdjustableClock(), RecordingHaptics(), bootId = { "boot" },
    )

    private class AdjustableClock : WearSessionClock {
        private var elapsedMs = 0L
        override fun elapsedRealtimeMs(): Long = elapsedMs
        override fun epochMillis(): Long = 1_000 + elapsedMs
        fun advanceBy(milliseconds: Long) { elapsedMs += milliseconds }
    }

    private class RecordingStore(private val events: MutableList<String> = mutableListOf()) : WearSessionRecordStore {
        private var record: SessionRecord? = null
        val checkpointBatches = mutableListOf<List<HeartRateSample>>()
        var failNextCheckpoint = false
        var getCalls = 0
        override suspend fun get(id: String): SessionRecord? {
            getCalls += 1
            return record?.takeIf { it.id == id }
        }
        override suspend fun upsert(record: SessionRecord) {
            this.record = record
            events += "persist:${record.status}"
        }
        override suspend fun enqueueCompleted(eventId: String, sessionId: String, revision: Long, payload: String) {
            events += "outbox:$sessionId"
        }
        override suspend fun completeWithOutbox(record: SessionRecord, eventId: String, payload: String) {
            this.record = record
            events += "persist:${record.status}"
            events += "outbox:${record.id}"
        }
        override suspend fun appendHeartRateBatch(record: SessionRecord, samples: List<HeartRateSample>) {
            checkpoint(record, samples)
        }
        override suspend fun checkpoint(record: SessionRecord, samples: List<HeartRateSample>) {
            if (failNextCheckpoint) {
                failNextCheckpoint = false
                throw IllegalStateException("disk full")
            }
            val merged = ((this.record?.heartRateSamples ?: emptyList()) + samples)
                .distinctBy(HeartRateSample::epochMillis)
                .sortedBy(HeartRateSample::epochMillis)
            this.record = record.copy(heartRateSamples = merged)
            if (samples.isNotEmpty()) checkpointBatches += samples
            events += "persist:${record.status}"
        }
        override suspend fun completeWithOutbox(record: SessionRecord, samples: List<HeartRateSample>, eventId: String, payload: String) {
            checkpoint(record, samples)
            events += "outbox:${record.id}"
        }
        fun latest(): SessionRecord? = record
        fun seed(record: SessionRecord) { this.record = record }
    }

    private class RecordingSnapshots : SessionSnapshotStore {
        var snapshot: WearSessionSnapshot? = null
        var saveCount = 0
        override suspend fun save(snapshot: WearSessionSnapshot) { this.snapshot = snapshot; saveCount += 1 }
        override suspend fun load(): WearSessionSnapshot? = snapshot
    }

    private class RecordingHealth(
        private val events: MutableList<String> = mutableListOf(),
        private val startResult: WearHealthStartResult = WearHealthStartResult.Started,
    ) : WearHealthClient {
        var ended = false
        var starts = 0
        override val updates: Flow<WearHealthUpdate> = emptyFlow()
        override suspend fun start(): WearHealthStartResult { starts += 1; return startResult }
        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        override suspend fun end() { ended = true; events += "health:end" }
        override suspend fun reattach(): WearHealthStartResult { starts += 1; return startResult }
    }

    private class RecordingHaptics : HapticCuePlayer {
        val cues = mutableListOf<Cue>()
        override fun play(cue: Cue) { cues += cue }
    }
}
