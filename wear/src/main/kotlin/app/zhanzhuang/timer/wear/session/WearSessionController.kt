package app.zhanzhuang.timer.wear.session

import android.os.SystemClock
import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SampleAccuracy
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.data.WearSessionRepository
import app.zhanzhuang.timer.wear.health.DurationOnlyReason
import app.zhanzhuang.timer.wear.health.WearHealthClient
import app.zhanzhuang.timer.wear.health.WearHealthStartResult
import app.zhanzhuang.timer.wear.health.WearHealthUpdate
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WearSessionUiState(
    val record: SessionRecord?,
    val remainingMs: Long,
    val currentHeartRateBpm: Double?,
    val nextReminderAtActiveMs: Long?,
    val heartRateUnavailableReason: String? = null,
)

interface WearSessionController {
    val state: StateFlow<WearSessionUiState>
    suspend fun start(config: SessionConfig, sessionId: String = UUID.randomUUID().toString())
    suspend fun pause()
    suspend fun resume()
    suspend fun finish(cancelled: Boolean = false)
    suspend fun tick()
    suspend fun recover(currentBootId: String)
}

interface WearSessionClock {
    fun elapsedRealtimeMs(): Long
    fun epochMillis(): Long
}

object AndroidWearSessionClock : WearSessionClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun epochMillis(): Long = System.currentTimeMillis()
}

/** The minimal record/outbox boundary needed by the controller; Task 6 owns transmission and ACKs. */
interface WearSessionRecordStore {
    suspend fun get(id: String): SessionRecord?
    suspend fun upsert(record: SessionRecord)
    suspend fun enqueueCompleted(eventId: String, sessionId: String, revision: Long, payload: String)
}

class WearSessionControllerImpl(
    private val records: WearSessionRecordStore,
    private val snapshots: SessionSnapshotStore,
    private val health: WearHealthClient,
    private val clock: WearSessionClock = AndroidWearSessionClock,
    private val haptics: HapticCuePlayer,
    private val bootId: () -> String = { System.getProperty("ro.boot_id") ?: "unknown" },
) : WearSessionController {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(emptyState())
    override val state: StateFlow<WearSessionUiState> = _state.asStateFlow()

    private var record: SessionRecord? = null
    private var runningSinceBootMs: Long? = null
    private var nextReminderAtActiveMs: Long? = null
    private var healthExerciseOwned = false
    private var currentHeartRateBpm: Double? = null
    private var heartRateUnavailableReason: String? = null
    private var lastStoredSampleAtBootMs = 0L
    private var samplesSinceStore = 0

    override suspend fun start(config: SessionConfig, sessionId: String) = mutex.withLock {
        check(record?.status !in ACTIVE_STATUSES) { "A Wear session is already active" }
        record = SessionRecord(
            id = sessionId,
            revision = 0,
            config = config,
            status = SessionStatus.STARTING,
            owner = SessionOwner.WEAR,
            startEpochMillis = clock.epochMillis(),
        )
        runningSinceBootMs = null
        nextReminderAtActiveMs = config.intervalMinutes * MINUTE_MS
        healthExerciseOwned = false
        currentHeartRateBpm = null
        heartRateUnavailableReason = null
        persistSnapshotAndRecord()

        when (val result = health.start()) {
            WearHealthStartResult.Started -> healthExerciseOwned = true
            is WearHealthStartResult.StartedWithoutUpdates -> {
                healthExerciseOwned = true
                heartRateUnavailableReason = result.reason.name
            }
            is WearHealthStartResult.DurationOnly -> {
                healthExerciseOwned = false
                heartRateUnavailableReason = result.reason.name
            }
        }
        record = record!!.copy(status = SessionStatus.RUNNING)
        runningSinceBootMs = clock.elapsedRealtimeMs()
        persistSnapshotAndRecord()
        haptics.play(Cue.START)
    }

    override suspend fun pause() = mutex.withLock {
        val active = record?.takeIf { it.status == SessionStatus.RUNNING } ?: return
        record = active.copy(status = SessionStatus.PAUSED, activeDurationMs = activeDurationNow())
        runningSinceBootMs = null
        persistSnapshotAndRecord()
        if (healthExerciseOwned) health.pause()
        haptics.play(Cue.PAUSE)
    }

    override suspend fun resume() = mutex.withLock {
        val paused = record?.takeIf { it.status == SessionStatus.PAUSED } ?: return
        record = paused.copy(status = SessionStatus.RUNNING)
        runningSinceBootMs = clock.elapsedRealtimeMs()
        persistSnapshotAndRecord()
        if (healthExerciseOwned) health.resume()
        haptics.play(Cue.RESUME)
    }

    override suspend fun finish(cancelled: Boolean) = mutex.withLock { finishLocked(cancelled) }

    override suspend fun tick() = mutex.withLock {
        val running = record?.takeIf { it.status == SessionStatus.RUNNING } ?: return
        val activeDuration = activeDurationNow()
        if (activeDuration >= running.config.durationMinutes * MINUTE_MS) {
            finishLocked(cancelled = false, activeDurationMs = activeDuration)
            return
        }
        val overdueReminder = nextReminderAtActiveMs?.takeIf { activeDuration >= it }
        if (overdueReminder != null) {
            nextReminderAtActiveMs = ((activeDuration / intervalMs()) + 1) * intervalMs()
            haptics.play(Cue.INTERVAL) // A delayed wake is deliberately coalesced to one cue.
        }
        if (clock.elapsedRealtimeMs() - lastStoredSampleAtBootMs >= SNAPSHOT_INTERVAL_MS) {
            record = running.copy(activeDurationMs = activeDuration)
            runningSinceBootMs = clock.elapsedRealtimeMs()
            persistSnapshotAndRecord()
        } else {
            publishState()
        }
    }

    /** Called by the service's single collector for Health Services callbacks. */
    suspend fun acceptHealthUpdate(update: WearHealthUpdate) = mutex.withLock {
        when (update) {
            is WearHealthUpdate.AvailabilityChanged -> {
                heartRateUnavailableReason = if (update.heartRateAvailable) null else DurationOnlyReason.HEART_RATE_UNAVAILABLE.name
                publishState()
            }
            is WearHealthUpdate.HeartRate -> {
                val active = record?.takeIf { it.status in ACTIVE_STATUSES } ?: return
                val sample = HeartRateSample(clock.epochMillis(), update.bpm, SampleAccuracy.HIGH)
                record = active.copy(heartRateSamples = active.heartRateSamples + sample)
                currentHeartRateBpm = update.bpm
                samplesSinceStore += 1
                // The snapshot contains every sample immediately; database writes are batched at 30s/20 samples.
                saveSnapshotOnly()
                if (samplesSinceStore >= SAMPLE_BATCH_COUNT || clock.elapsedRealtimeMs() - lastStoredSampleAtBootMs >= SAMPLE_BATCH_INTERVAL_MS) {
                    persistSnapshotAndRecord()
                } else {
                    publishState()
                }
            }
        }
    }

    override suspend fun recover(currentBootId: String) = mutex.withLock {
        val snapshot = snapshots.load() ?: return
        record = snapshot.record
        runningSinceBootMs = snapshot.runningSinceBootMs
        nextReminderAtActiveMs = snapshot.nextReminderAtActiveMs
        healthExerciseOwned = snapshot.healthExerciseOwned
        if (snapshot.bootId != currentBootId && record!!.status in ACTIVE_STATUSES) {
            record = record!!.copy(status = SessionStatus.INTERRUPTED, endEpochMillis = clock.epochMillis())
            runningSinceBootMs = null
            healthExerciseOwned = false
            persistSnapshotAndRecord(currentBootId)
            return
        }
        if (record!!.status == SessionStatus.RUNNING && runningSinceBootMs != null) {
            record = record!!.copy(activeDurationMs = activeDurationNow())
            runningSinceBootMs = clock.elapsedRealtimeMs()
        }
        persistSnapshotAndRecord(currentBootId)
        if (record!!.status in ACTIVE_STATUSES && healthExerciseOwned) {
            when (val result = health.start()) {
                WearHealthStartResult.Started -> Unit
                is WearHealthStartResult.StartedWithoutUpdates -> heartRateUnavailableReason = result.reason.name
                is WearHealthStartResult.DurationOnly -> {
                    healthExerciseOwned = false
                    heartRateUnavailableReason = result.reason.name
                    persistSnapshotAndRecord(currentBootId)
                }
            }
        }
    }

    private suspend fun finishLocked(cancelled: Boolean, activeDurationMs: Long = activeDurationNow()) {
        val active = record?.takeIf { it.status in ACTIVE_STATUSES } ?: return
        record = active.copy(status = SessionStatus.COMPLETING, activeDurationMs = activeDurationMs)
        runningSinceBootMs = null
        persistSnapshotAndRecord()
        if (healthExerciseOwned) health.end()
        healthExerciseOwned = false
        val terminalStatus = if (cancelled) SessionStatus.CANCELLED else SessionStatus.COMPLETED
        record = record!!.copy(status = terminalStatus, endEpochMillis = clock.epochMillis())
        persistSnapshotAndRecord()
        if (!cancelled) {
            val complete = record!!
            records.enqueueCompleted("${complete.id}:${complete.revision}", complete.id, complete.revision, complete.toOutboxPayload())
        }
        haptics.play(if (cancelled) Cue.PAUSE else Cue.COMPLETE)
    }

    private fun activeDurationNow(): Long {
        val current = record ?: return 0
        val start = runningSinceBootMs ?: return current.activeDurationMs
        return current.activeDurationMs + (clock.elapsedRealtimeMs() - start).coerceAtLeast(0)
    }

    private fun intervalMs(): Long = record!!.config.intervalMinutes * MINUTE_MS

    private suspend fun persistSnapshotAndRecord(snapshotBootId: String = bootId()) {
        record = record!!.copy(revision = record!!.revision + 1)
        saveSnapshotOnly(snapshotBootId)
        records.upsert(record!!)
        lastStoredSampleAtBootMs = clock.elapsedRealtimeMs()
        samplesSinceStore = 0
        publishState()
    }

    private suspend fun saveSnapshotOnly(snapshotBootId: String = bootId()) {
        snapshots.save(
            WearSessionSnapshot(
                record = record!!,
                bootId = snapshotBootId,
                runningSinceBootMs = runningSinceBootMs,
                nextReminderAtActiveMs = nextReminderAtActiveMs,
                healthExerciseOwned = healthExerciseOwned,
            ),
        )
    }

    private fun publishState() {
        val current = record
        _state.value = WearSessionUiState(
            record = current,
            remainingMs = current?.let { (it.config.durationMinutes * MINUTE_MS - activeDurationNow()).coerceAtLeast(0) } ?: 0,
            currentHeartRateBpm = currentHeartRateBpm,
            nextReminderAtActiveMs = nextReminderAtActiveMs,
            heartRateUnavailableReason = heartRateUnavailableReason,
        )
    }

    private fun SessionRecord.toOutboxPayload(): String = "{\"id\":\"$id\",\"revision\":$revision,\"status\":\"$status\"}"

    private companion object {
        const val MINUTE_MS = 60_000L
        const val SNAPSHOT_INTERVAL_MS = 60_000L
        const val SAMPLE_BATCH_INTERVAL_MS = 30_000L
        const val SAMPLE_BATCH_COUNT = 20
        val ACTIVE_STATUSES = setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
        fun emptyState() = WearSessionUiState(null, 0, null, null)
    }
}

class RepositoryRecordStore(
    private val repository: WearSessionRepository,
) : WearSessionRecordStore {
    override suspend fun get(id: String): SessionRecord? = repository.get(id)
    override suspend fun upsert(record: SessionRecord) = repository.upsert(record)
    override suspend fun enqueueCompleted(eventId: String, sessionId: String, revision: Long, payload: String) =
        repository.enqueueCompleted(eventId, sessionId, revision, payload)
}
