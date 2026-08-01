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

    private class AdjustableClock : WearSessionClock {
        private var elapsedMs = 0L
        override fun elapsedRealtimeMs(): Long = elapsedMs
        override fun epochMillis(): Long = 1_000 + elapsedMs
        fun advanceBy(milliseconds: Long) { elapsedMs += milliseconds }
    }

    private class RecordingStore(private val events: MutableList<String> = mutableListOf()) : WearSessionRecordStore {
        private var record: SessionRecord? = null
        override suspend fun get(id: String): SessionRecord? = record?.takeIf { it.id == id }
        override suspend fun upsert(record: SessionRecord) {
            this.record = record
            events += "persist:${record.status}"
        }
        override suspend fun enqueueCompleted(eventId: String, sessionId: String, revision: Long, payload: String) {
            events += "outbox:$sessionId"
        }
        fun latest(): SessionRecord? = record
    }

    private class RecordingSnapshots : SessionSnapshotStore {
        var snapshot: WearSessionSnapshot? = null
        override suspend fun save(snapshot: WearSessionSnapshot) { this.snapshot = snapshot }
        override suspend fun load(): WearSessionSnapshot? = snapshot
    }

    private class RecordingHealth(
        private val events: MutableList<String> = mutableListOf(),
        private val startResult: WearHealthStartResult = WearHealthStartResult.Started,
    ) : WearHealthClient {
        var ended = false
        override val updates: Flow<WearHealthUpdate> = emptyFlow()
        override suspend fun start(): WearHealthStartResult = startResult
        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        override suspend fun end() { ended = true; events += "health:end" }
    }

    private class RecordingHaptics : HapticCuePlayer {
        val cues = mutableListOf<Cue>()
        override fun play(cue: Cue) { cues += cue }
    }
}
