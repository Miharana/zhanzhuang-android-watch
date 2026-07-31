package app.zhanzhuang.timer.mobile.session

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.mobile.data.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private class FakeElapsedClock : ElapsedClock {
        private var now = 0L
        override fun nowMillis(): Long = now
        fun advanceBy(milliseconds: Long) {
            now += milliseconds
        }
    }

    private class FakeWallClock : WallClock {
        override fun nowMillis(): Long = 1_000_000L
    }

    private class FakeHaptics : MobileHaptics {
        var completionCount = 0
        override fun start() = Unit
        override fun interval() = Unit
        override fun pauseResume() = Unit
        override fun completion() {
            completionCount += 1
        }
    }

    private class InMemorySessionRepository : SessionRepository {
        private val records = linkedMapOf<String, SessionRecord>()
        private val flow = MutableStateFlow<List<SessionRecord>>(emptyList())

        override fun observeSessions(): Flow<List<SessionRecord>> = flow
        override suspend fun get(id: String): SessionRecord? = records[id]
        override suspend fun activeSession(): SessionRecord? = records.values.lastOrNull {
            it.status in setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
        }
        override suspend fun upsert(record: SessionRecord) {
            if (record.revision > (records[record.id]?.revision ?: -1)) {
                records[record.id] = record
                flow.value = records.values.toList()
            }
        }
        fun activeOrLast(): SessionRecord? = records.values.lastOrNull()
    }
}
