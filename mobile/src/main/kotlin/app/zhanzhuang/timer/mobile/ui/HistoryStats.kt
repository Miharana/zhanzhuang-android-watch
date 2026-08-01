package app.zhanzhuang.timer.mobile.ui

import app.zhanzhuang.timer.model.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class HistoryStats(
    val todayActiveMs: Long,
    val weekActiveMs: Long,
    val monthActiveMs: Long,
) {
    companion object {
        fun calculate(records: List<SessionRecord>, now: Instant, zoneId: ZoneId): HistoryStats {
            val localNow = now.atZone(zoneId)
            val todayStart = localNow.toLocalDate().atStartOfDay(zoneId).toInstant()
            val weekStart = localNow.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay(zoneId)
                .toInstant()
            // “Month” is deliberately the last 30 local calendar days, so the
            // statistic remains useful on the first day of a calendar month.
            val monthStart = localNow.toLocalDate().minusDays(29).atStartOfDay(zoneId).toInstant()

            fun totalSince(start: Instant) = records
                .filter { record -> record.startEpochMillis?.let { Instant.ofEpochMilli(it) }?.isBefore(now) == true }
                .filter { record -> record.startEpochMillis?.let { Instant.ofEpochMilli(it) }?.let { !it.isBefore(start) } == true }
                .sumOf(SessionRecord::activeDurationMs)

            return HistoryStats(
                todayActiveMs = totalSince(todayStart),
                weekActiveMs = totalSince(weekStart),
                monthActiveMs = totalSince(monthStart),
            )
        }
    }
}
