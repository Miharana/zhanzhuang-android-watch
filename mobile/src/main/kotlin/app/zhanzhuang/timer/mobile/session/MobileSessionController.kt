package app.zhanzhuang.timer.mobile.session

import app.zhanzhuang.timer.domain.ClockSnapshot
import app.zhanzhuang.timer.domain.SessionClock
import app.zhanzhuang.timer.mobile.data.MobileRuntimeSnapshot
import app.zhanzhuang.timer.mobile.data.SessionRepository
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import java.util.UUID
import kotlin.math.abs
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

fun interface HealthSyncScheduler {
    fun enqueue(record: SessionRecord): HealthSyncEnqueueResult
}

sealed interface HealthSyncEnqueueResult {
    data object Enqueued : HealthSyncEnqueueResult
    data object NotEligible : HealthSyncEnqueueResult
    data object Failed : HealthSyncEnqueueResult
}

data class MobileSessionUiState(
    val session: SessionRecord? = null,
    val activeElapsedMs: Long = 0,
    val remainingMs: Long = 0,
)

interface MobileSessionController {
    val state: StateFlow<MobileSessionUiState>
    suspend fun recover(): Boolean
    suspend fun start(config: SessionConfig, sessionId: String? = null)
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
    private val healthSyncScheduler: HealthSyncScheduler = HealthSyncScheduler { HealthSyncEnqueueResult.NotEligible },
) : MobileSessionController {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(MobileSessionUiState())
    override val state = mutableState.asStateFlow()

    private var record: SessionRecord? = null
    private var sessionClock: SessionClock? = null
    private var pausedAtElapsedMs: Long? = null
    private var startedAtElapsedMs: Long? = null
    private var acknowledgedReminderIndex = 0
    private var lastPersistedActiveMs = 0L

    override suspend fun start(config: SessionConfig, sessionId: String?) = mutex.withLock {
        val existing = repository.activeSession()
        check(existing == null || existing.id == sessionId) { "A session is already active" }
        val nowElapsed = elapsedClock.nowMillis()
        val started = (existing ?: SessionRecord(
            id = sessionId ?: UUID.randomUUID().toString(),
            revision = 0,
            config = config,
            owner = SessionOwner.MOBILE,
            startEpochMillis = wallClock.nowMillis(),
        )).copy(
            config = config,
            revision = (existing?.revision ?: 0) + 1,
            status = SessionStatus.RUNNING,
            owner = SessionOwner.MOBILE,
            startEpochMillis = existing?.startEpochMillis ?: wallClock.nowMillis(),
        )
        record = started
        sessionClock = SessionClock(config, nowElapsed)
        startedAtElapsedMs = nowElapsed
        pausedAtElapsedMs = null
        acknowledgedReminderIndex = 0
        lastPersistedActiveMs = 0
        val snapshot = sessionClock!!.snapshot(nowElapsed)
        persist(started, snapshot)
        haptics.start()
    }

    override suspend fun recover(): Boolean = mutex.withLock {
        val runtime = repository.runtimeSnapshot()
        if (runtime == null) {
            val activeSession = repository.activeSession()
            if (activeSession != null) interrupt(activeSession)
            return false
        }
        val persisted = repository.get(runtime.sessionId) ?: return false
        if (persisted.status !in ACTIVE_STATUSES || hasUnsafeAnchors(runtime)) {
            if (persisted.status in ACTIVE_STATUSES) interrupt(persisted)
            return false
        }
        if (persisted.status == SessionStatus.PAUSED && runtime.pausedAtElapsedMs == null) {
            interrupt(persisted)
            return false
        }

        startedAtElapsedMs = runtime.startedAtElapsedMs
        pausedAtElapsedMs = runtime.pausedAtElapsedMs
        acknowledgedReminderIndex = runtime.acknowledgedReminderIndex
        sessionClock = SessionClock.restore(
            config = persisted.config,
            startedAtElapsedMs = runtime.startedAtElapsedMs,
            pausedAtElapsedMs = runtime.pausedAtElapsedMs,
            pausedDurationMs = persisted.pausedDurationMs,
            lastAcknowledgedReminderIndex = runtime.acknowledgedReminderIndex,
        )
        record = persisted
        lastPersistedActiveMs = persisted.activeDurationMs
        mutableState.value = uiState(persisted, sessionClock!!.snapshot(elapsedClock.nowMillis()))
        true
    }

    override suspend fun pause() = mutex.withLock {
        val current = record ?: return
        if (current.status != SessionStatus.RUNNING) return
        val nowElapsed = elapsedClock.nowMillis()
        val clock = requireNotNull(sessionClock)
        val snapshot = clock.snapshot(nowElapsed)
        clock.pause(nowElapsed)
        pausedAtElapsedMs = nowElapsed
        persist(current.copy(
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
        persist(current.copy(
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
        val dueReminderIndex = snapshot.dueReminderIndex
        if (dueReminderIndex != null && !snapshot.isCompleted) {
            haptics.interval()
            requireNotNull(sessionClock).acknowledgeReminder(dueReminderIndex)
            acknowledgedReminderIndex = dueReminderIndex
            persist(current.copy(
                revision = current.revision + 1,
                activeDurationMs = snapshot.activeElapsedMs,
            ), snapshot)
            return
        }
        if (snapshot.isCompleted) {
            finishLocked(cancelled = false, snapshot = snapshot)
        } else if (snapshot.activeElapsedMs - lastPersistedActiveMs >= PERSIST_INTERVAL_MS) {
            persist(current.copy(
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
        record = finished
        lastPersistedActiveMs = currentSnapshot.activeElapsedMs
        repository.upsert(finished)
        mutableState.value = uiState(finished, currentSnapshot)
        if (!cancelled && finished.activeDurationMs >= 60_000L) {
            val pendingExport = finished.copy(
                revision = finished.revision + 1,
                healthConnectState = app.zhanzhuang.timer.model.SyncState.PENDING,
            )
            record = pendingExport
            repository.upsert(pendingExport)
            mutableState.value = uiState(pendingExport, currentSnapshot)
            val enqueueResult = runCatching { healthSyncScheduler.enqueue(pendingExport) }
                .getOrDefault(HealthSyncEnqueueResult.Failed)
            if (enqueueResult != HealthSyncEnqueueResult.Enqueued) {
                val localResult = pendingExport.copy(
                    revision = pendingExport.revision + 1,
                    healthConnectState = if (enqueueResult == HealthSyncEnqueueResult.NotEligible) {
                        app.zhanzhuang.timer.model.SyncState.LOCAL_ONLY
                    } else {
                        app.zhanzhuang.timer.model.SyncState.FAILED
                    },
                )
                record = localResult
                repository.upsert(localResult)
                mutableState.value = uiState(localResult, currentSnapshot)
            }
        }
        if (!cancelled) haptics.completion()
    }

    private suspend fun persist(updated: SessionRecord, snapshot: ClockSnapshot) {
        record = updated
        lastPersistedActiveMs = snapshot.activeElapsedMs
        repository.upsert(updated, runtimeSnapshot())
        mutableState.value = uiState(updated, snapshot)
    }

    private fun runtimeSnapshot() = MobileRuntimeSnapshot(
        sessionId = requireNotNull(record).id,
        startedAtElapsedMs = requireNotNull(startedAtElapsedMs),
        checkpointElapsedMs = elapsedClock.nowMillis(),
        checkpointWallEpochMs = wallClock.nowMillis(),
        pausedAtElapsedMs = pausedAtElapsedMs,
        acknowledgedReminderIndex = acknowledgedReminderIndex,
    )

    private suspend fun interrupt(current: SessionRecord) {
        val interrupted = current.copy(
            revision = current.revision + 1,
            status = SessionStatus.INTERRUPTED,
            endEpochMillis = wallClock.nowMillis(),
        )
        record = interrupted
        repository.upsert(interrupted)
        mutableState.value = MobileSessionUiState(
            session = interrupted,
            activeElapsedMs = interrupted.activeDurationMs,
            remainingMs = (interrupted.config.durationMinutes * 60_000L - interrupted.activeDurationMs).coerceAtLeast(0),
        )
    }

    private fun hasUnsafeAnchors(runtime: MobileRuntimeSnapshot): Boolean {
        val elapsedSinceCheckpoint = elapsedClock.nowMillis() - runtime.checkpointElapsedMs
        val wallSinceCheckpoint = wallClock.nowMillis() - runtime.checkpointWallEpochMs
        return elapsedSinceCheckpoint < 0 ||
            wallSinceCheckpoint < 0 ||
            abs(elapsedSinceCheckpoint - wallSinceCheckpoint) > MAX_ANCHOR_DRIFT_MS
    }

    private fun uiState(record: SessionRecord, snapshot: ClockSnapshot) = MobileSessionUiState(
        session = record,
        activeElapsedMs = snapshot.activeElapsedMs,
        remainingMs = snapshot.remainingMs,
    )

    private companion object {
        const val PERSIST_INTERVAL_MS = 60_000L
        const val MAX_ANCHOR_DRIFT_MS = 120_000L
        val ACTIVE_STATUSES = setOf(
            SessionStatus.STARTING,
            SessionStatus.RUNNING,
            SessionStatus.PAUSED,
            SessionStatus.COMPLETING,
        )
    }
}
