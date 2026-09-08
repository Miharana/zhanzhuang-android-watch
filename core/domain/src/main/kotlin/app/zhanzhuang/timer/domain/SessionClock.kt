package app.zhanzhuang.timer.domain

import app.zhanzhuang.timer.model.SessionConfig

data class ClockSnapshot(
    val activeElapsedMs: Long,
    val remainingMs: Long,
    val dueReminderIndex: Int?,
    val isCompleted: Boolean,
)

class SessionClock(
    private val config: SessionConfig,
    private val startedAtElapsedMs: Long,
) {
    private var pausedAtElapsedMs: Long? = null
    private var pausedDurationMs = 0L
    private var lastAcknowledgedReminderIndex = 0

    private constructor(
        config: SessionConfig,
        startedAtElapsedMs: Long,
        pausedAtElapsedMs: Long?,
        pausedDurationMs: Long,
        lastAcknowledgedReminderIndex: Int,
    ) : this(config, startedAtElapsedMs) {
        this.pausedAtElapsedMs = pausedAtElapsedMs
        this.pausedDurationMs = pausedDurationMs
        this.lastAcknowledgedReminderIndex = lastAcknowledgedReminderIndex
    }

    fun pause(nowElapsedMs: Long) {
        require(nowElapsedMs >= startedAtElapsedMs)
        if (pausedAtElapsedMs == null) pausedAtElapsedMs = nowElapsedMs
    }

    fun resume(nowElapsedMs: Long) {
        val pausedAt = pausedAtElapsedMs ?: return
        require(nowElapsedMs >= pausedAt)
        pausedDurationMs += nowElapsedMs - pausedAt
        pausedAtElapsedMs = null
    }

    fun acknowledgeReminder(index: Int) {
        require(index > lastAcknowledgedReminderIndex)
        lastAcknowledgedReminderIndex = index
    }

    fun snapshot(nowElapsedMs: Long): ClockSnapshot {
        val activeElapsedMs = activeElapsedAt(nowElapsedMs)
        val totalDurationMs = config.durationMinutes * MINUTE_MS
        val dueReminderIndex = dueReminderIndex(activeElapsedMs, totalDurationMs)
        return ClockSnapshot(
            activeElapsedMs = activeElapsedMs,
            remainingMs = (totalDurationMs - activeElapsedMs).coerceAtLeast(0),
            dueReminderIndex = dueReminderIndex,
            isCompleted = activeElapsedMs >= totalDurationMs,
        )
    }

    private fun activeElapsedAt(nowElapsedMs: Long): Long {
        val effectiveNow = pausedAtElapsedMs ?: nowElapsedMs
        require(effectiveNow >= startedAtElapsedMs)
        return (effectiveNow - startedAtElapsedMs - pausedDurationMs).coerceAtLeast(0)
    }

    private fun dueReminderIndex(activeElapsedMs: Long, totalDurationMs: Long): Int? {
        val intervalMs = config.intervalMinutes * MINUTE_MS
        val latestDueIndex = minOf(activeElapsedMs, totalDurationMs) / intervalMs
        if (latestDueIndex <= lastAcknowledgedReminderIndex) return null
        return latestDueIndex.toInt()
    }

    companion object {
        fun restore(
            config: SessionConfig,
            startedAtElapsedMs: Long,
            pausedAtElapsedMs: Long?,
            pausedDurationMs: Long,
            lastAcknowledgedReminderIndex: Int,
        ) = SessionClock(
            config = config,
            startedAtElapsedMs = startedAtElapsedMs,
            pausedAtElapsedMs = pausedAtElapsedMs,
            pausedDurationMs = pausedDurationMs,
            lastAcknowledgedReminderIndex = lastAcknowledgedReminderIndex,
        )

        const val MINUTE_MS = 60_000L
    }
}
