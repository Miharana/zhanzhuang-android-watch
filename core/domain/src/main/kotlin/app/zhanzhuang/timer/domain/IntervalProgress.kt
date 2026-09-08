package app.zhanzhuang.timer.domain

import app.zhanzhuang.timer.model.SessionConfig

/**
 * The fraction of the current reminder interval that has elapsed.  A full
 * revolution intentionally resets to zero exactly when the next reminder is
 * due, so the running ring always represents one interval rather than the
 * whole session.
 */
fun intervalProgress(activeElapsedMs: Long, config: SessionConfig): Float {
    val intervalMs = config.intervalMinutes * 60_000L
    val elapsedInInterval = activeElapsedMs.coerceAtLeast(0) % intervalMs
    return elapsedInInterval.toFloat() / intervalMs.toFloat()
}
