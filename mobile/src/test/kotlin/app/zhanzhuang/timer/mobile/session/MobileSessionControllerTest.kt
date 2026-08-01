package app.zhanzhuang.timer.mobile.session

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.mobile.data.MobileRuntimeSnapshot
import app.zhanzhuang.timer.mobile.data.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileSessionControllerTest {
    private val elapsed = FakeElapsedClock()
    private val wall = FakeWallClock()
    private val haptics = FakeHaptics()
    private val repository = InMemorySessionRepository()
    private val controller = DefaultMobileSessionController(repository, elapsed, wall, haptics)

    @Test
    fun phoneCompletesAndVibratesOnce() = runTest {
        controller.start(SessionConfig(15, 5))
        elapsed.advanceBy(900_000)

        controller.tick()
        controller.tick()

        assertEquals(1, haptics.completionCount)
        assertEquals(SessionStatus.COMPLETED, repository.activeOrLast()!!.status)
    }

    @Test
    fun intervalCueIsAcknowledgedAndOnlyVibratesOnce() = runTest {
        controller.start(SessionConfig(15, 5))
        elapsed.advanceBy(300_000)

        controller.tick()
        controller.tick()
        controller.tick()

        assertEquals(1, haptics.intervalCount)
        assertEquals(1, repository.runtime!!.acknowledgedReminderIndex)
    }

    @Test
    fun recoveredAcknowledgedIntervalDoesNotVibrateAgain() = runTest {
        controller.start(SessionConfig(15, 5))
        elapsed.advanceBy(300_000)
        controller.tick()
        val recovered = DefaultMobileSessionController(repository, elapsed, wall, haptics)

        assertTrue(recovered.recover())
        recovered.tick()

        assertEquals(1, haptics.intervalCount)
    }

    @Test
    fun sameBootRunningSessionRecoversFromMonotonicAnchor() = runTest {
        controller.start(SessionConfig(15, 5))
        elapsed.advanceBy(120_000)
        controller.tick()
        val recovered = DefaultMobileSessionController(repository, elapsed, wall, haptics)

        assertTrue(recovered.recover())
        assertEquals(SessionStatus.RUNNING, recovered.state.value.session!!.status)
        assertEquals(120_000, recovered.state.value.activeElapsedMs)
    }

    @Test
    fun pausedSessionRecoversWithoutAdvancingElapsedDuration() = runTest {
        controller.start(SessionConfig(15, 5))
        elapsed.advanceBy(120_000)
        controller.pause()
        val recovered = DefaultMobileSessionController(repository, elapsed, wall, haptics)

        assertTrue(recovered.recover())
        elapsed.advanceBy(300_000)
        recovered.tick()

        assertEquals(SessionStatus.PAUSED, recovered.state.value.session!!.status)
        assertEquals(120_000, recovered.state.value.activeElapsedMs)
    }

    @Test
    fun elapsedAnchorRegressionInterruptsInsteadOfInventingTime() = runTest {
        controller.start(SessionConfig(15, 5))
        elapsed.advanceBy(120_000)
        controller.tick()
        elapsed.setTo(10)
        val recovered = DefaultMobileSessionController(repository, elapsed, wall, haptics)

        assertFalse(recovered.recover())
        assertEquals(SessionStatus.INTERRUPTED, repository.activeOrLast()!!.status)
        assertEquals(120_000, repository.activeOrLast()!!.activeDurationMs)
    }

    private class FakeElapsedClock : ElapsedClock {
        private var now = 0L
        override fun nowMillis(): Long = now
        fun advanceBy(milliseconds: Long) {
            now += milliseconds
        }
        fun setTo(milliseconds: Long) {
            now = milliseconds
        }
    }

    private class FakeWallClock : WallClock {
        override fun nowMillis(): Long = 1_000_000L
    }

    private class FakeHaptics : MobileHaptics {
        var completionCount = 0
        var intervalCount = 0
        override fun start() = Unit
        override fun interval() {
            intervalCount += 1
        }
        override fun pauseResume() = Unit
        override fun completion() {
            completionCount += 1
        }
    }

    private class InMemorySessionRepository : SessionRepository {
        private val records = linkedMapOf<String, SessionRecord>()
        private val flow = MutableStateFlow<List<SessionRecord>>(emptyList())
        var runtime: MobileRuntimeSnapshot? = null

        override fun observeSessions(): Flow<List<SessionRecord>> = flow
        override suspend fun get(id: String): SessionRecord? = records[id]
        override suspend fun runtimeSnapshot(): MobileRuntimeSnapshot? = runtime
        override suspend fun activeSession(): SessionRecord? = records.values.lastOrNull {
            it.status in setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
        }
        override suspend fun upsert(record: SessionRecord, runtimeSnapshot: MobileRuntimeSnapshot?) {
            if (record.revision > (records[record.id]?.revision ?: -1)) {
                records[record.id] = record
                runtime = runtimeSnapshot
                flow.value = records.values.toList()
            }
        }
        fun activeOrLast(): SessionRecord? = records.values.lastOrNull()
    }
}
