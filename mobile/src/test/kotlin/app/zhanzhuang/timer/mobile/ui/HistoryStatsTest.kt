package app.zhanzhuang.timer.mobile.ui

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryStatsTest {
    @Test
    fun totalsUseActiveDurationAndLocalZone() {
        val stats = HistoryStats.calculate(
            records = records(),
            now = instant("2026-08-01T12:00:00Z"),
            zoneId = ZoneId.of("Europe/London"),
        )

        assertEquals(30 * MINUTE, stats.todayActiveMs)
        assertEquals(90 * MINUTE, stats.weekActiveMs)
        assertEquals(180 * MINUTE, stats.monthActiveMs)
    }

    private fun records() = listOf(
        record("today", "2026-08-01T07:00:00Z", 30),
        record("week", "2026-07-30T07:00:00Z", 60),
        record("month", "2026-07-10T07:00:00Z", 90),
    )

    private fun record(id: String, start: String, minutes: Int, status: SessionStatus = SessionStatus.COMPLETED) = SessionRecord(
        id = id,
        config = SessionConfig(),
        status = status,
        startEpochMillis = instant(start).toEpochMilli(),
        endEpochMillis = instant(start).plusSeconds(minutes * 60L).toEpochMilli(),
        activeDurationMs = minutes * MINUTE,
    )

    private fun instant(value: String) = Instant.parse(value)

    private companion object { const val MINUTE = 60_000L }
}
