package app.zhanzhuang.timer.domain

import app.zhanzhuang.timer.model.SessionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionClockTest {
    @Test
    fun invalidConfigFails() {
        assertFailsWith<IllegalArgumentException> { SessionConfig(14, 10) }
        assertFailsWith<IllegalArgumentException> { SessionConfig(30, 7) }
    }

    @Test
    fun pauseIsExcludedAndLateWakeDoesNotBackfill() {
        val clock = SessionClock(SessionConfig(30, 10), 1_000)
        clock.pause(301_000)
        clock.resume(361_000)

        val snapshot = clock.snapshot(1_261_000)

        assertEquals(1_200_000, snapshot.activeElapsedMs)
        assertEquals(2, snapshot.dueReminderIndex)
    }
}
