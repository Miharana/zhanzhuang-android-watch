package app.zhanzhuang.timer.mobile.session

import app.zhanzhuang.timer.domain.ClockSnapshot
import app.zhanzhuang.timer.domain.SessionClock
import app.zhanzhuang.timer.mobile.data.SessionRepository
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface ElapsedClock { fun nowMillis(): Long }
fun interface WallClock { fun nowMillis(): Long }

interface MobileHaptics {
    fun start()
    fun interval()
    fun pauseResume()
    fun completion()
}

data class MobileSessionUiState(
    val session: SessionRecord? = null,
    val activeElapsedMs: Long = 0,
    val remainingMs: Long = 0,
)

interface MobileSessionController {
    val state: StateFlow<MobileSessionUiState>
    suspend fun start(config: SessionConfig)
    suspend fun pause()
    suspend fun resume()
    suspend fun finish(cancelled: Boolean = false)
    suspend fun tick()
}

class DefaultMobileSessionController(
    private val repository: SessionRepository,
    private val elapsedClock: ElapsedClock,
    private val wallClock: WallClock,
    private val haptics: MobileHaptics,
) : MobileSessionController {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(MobileSessionUiState())
    override val state = mutableState.asStateFlow()

    private var record: SessionRecord? = null
    private var sessionClock: SessionClock? = null
    private var pausedAtElapsedMs: Long? = null
    private var lastPersistedActiveMs = 0L

    override suspend fun start(config: SessionConfig) = mutex.withLock {
        check(repository.activeSession() == null) { "A session is already active" }
        val nowElapsed = elapsedClock.nowMillis()
        val started = SessionRecord(
            id = UUID.randomUUID().toString(),
            revision = 1,
            config = config,
            status = SessionStatus.RUNNING,
            owner = SessionOwner.MOBILE,
            startEpochMillis = wallClock.nowMillis(),
        )
        record = started
        sessionClock = SessionClock(config, nowElapsed)
        pausedAtElapsedMs = null
        lastPersistedActiveMs = 0
        repository.upsert(started)
        mutableState.value = uiState(started, sessionClock!!.snapshot(nowElapsed))
        haptics.start()
    }

    override suspend fun pause() = mutex.withLock {
        val current = record ?: return
        if (current.status != SessionStatus.RUNNING) return
        val nowElapsed = elapsedClock.nowMillis()
        val clock = requireNotNull(sessionClock)
        val snapshot = clock.snapshot(nowElapsed)
        clock.pause(nowElapsed)
        pausedAtElapsedMs = nowElapsed
        update(current.copy(
            revision = current.revision + 1,
            status = SessionStatus.PAUSED,
            activeDurationMs = snapshot.activeElapsedMs,
        ), snapshot)
        haptics.pauseResume()
    }

    override suspend fun resume() = mutex.withLock {
        val current = record ?: return
        if (current.status != SessionStatus.PAUSED) return
        val nowElapsed = elapsedClock.nowMillis()
        val pausedAt = requireNotNull(pausedAtElapsedMs)
        val clock = requireNotNull(sessionClock)
        clock.resume(nowElapsed)
        pausedAtElapsedMs = null
        val snapshot = clock.snapshot(nowElapsed)
        update(current.copy(
            revision = current.revision + 1,
            status = SessionStatus.RUNNING,
            activeDurationMs = snapshot.activeElapsedMs,
            pausedDurationMs = current.pausedDurationMs + (nowElapsed - pausedAt),
        ), snapshot)
        haptics.pauseResume()
    }

    override suspend fun finish(cancelled: Boolean) = mutex.withLock {
        finishLocked(cancelled)
    }

    override suspend fun tick() = mutex.withLock {
        val current = record ?: return
        if (current.status != SessionStatus.RUNNING) return
        val snapshot = requireNotNull(sessionClock).snapshot(elapsedClock.nowMillis())
        if (snapshot.dueReminderIndex != null && !snapshot.isCompleted) haptics.interval()
        if (snapshot.isCompleted) {
            finishLocked(cancelled = false, snapshot = snapshot)
        } else if (snapshot.activeElapsedMs - lastPersistedActiveMs >= PERSIST_INTERVAL_MS) {
            update(current.copy(
                revision = current.revision + 1,
                activeDurationMs = snapshot.activeElapsedMs,
            ), snapshot)
        } else {
            mutableState.value = uiState(current, snapshot)
        }
    }

    private suspend fun finishLocked(cancelled: Boolean, snapshot: ClockSnapshot? = null) {
        val current = record ?: return
        if (current.status !in ACTIVE_STATUSES) return
        val currentSnapshot = snapshot ?: requireNotNull(sessionClock).snapshot(elapsedClock.nowMillis())
        val finished = current.copy(
            revision = current.revision + 1,
            status = if (cancelled) SessionStatus.CANCELLED else SessionStatus.COMPLETED,
            endEpochMillis = wallClock.nowMillis(),
            activeDurationMs = currentSnapshot.activeElapsedMs,
        )
        update(finished, currentSnapshot)
        if (!cancelled) haptics.completion()
    }

    private suspend fun update(updated: SessionRecord, snapshot: ClockSnapshot) {
        record = updated
        lastPersistedActiveMs = snapshot.activeElapsedMs
        repository.upsert(updated)
        mutableState.value = uiState(updated, snapshot)
    }

    private fun uiState(record: SessionRecord, snapshot: ClockSnapshot) = MobileSessionUiState(
        session = record,
        activeElapsedMs = snapshot.activeElapsedMs,
        remainingMs = snapshot.remainingMs,
    )

    private companion object {
        const val PERSIST_INTERVAL_MS = 60_000L
        val ACTIVE_STATUSES = setOf(
            SessionStatus.STARTING,
            SessionStatus.RUNNING,
            SessionStatus.PAUSED,
            SessionStatus.COMPLETING,
        )
    }
}
