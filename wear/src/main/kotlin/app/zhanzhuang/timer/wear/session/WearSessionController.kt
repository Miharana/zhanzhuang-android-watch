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
    suspend fun recover(currentBootId: String): WearSessionRecovery
}

enum class WearSessionRecovery { NO_SESSION, ACTIVE, TERMINAL }

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
    suspend fun completeWithOutbox(record: SessionRecord, eventId: String, payload: String)
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
    private var pendingOperation: WearExternalOperation? = null
    private var pausedSinceBootMs: Long? = null
    private var intendedTerminalStatus: SessionStatus? = null
    private var currentHeartRateBpm: Double? = null
    private var heartRateUnavailableReason: String? = null
    private var lastStoredSampleAtBootMs = 0L
    private var samplesSinceStore = 0
    private var heartRateAcceptFromBootMs: Long? = null

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
        heartRateAcceptFromBootMs = null
        nextReminderAtActiveMs = config.intervalMinutes * MINUTE_MS
        healthExerciseOwned = false
        pendingOperation = WearExternalOperation.START
        intendedTerminalStatus = null
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
        pendingOperation = null
        record = record!!.copy(status = SessionStatus.RUNNING)
        runningSinceBootMs = clock.elapsedRealtimeMs()
        heartRateAcceptFromBootMs = runningSinceBootMs
        persistSnapshotAndRecord()
        haptics.play(Cue.START)
    }

    override suspend fun pause() = mutex.withLock {
        val active = record?.takeIf { it.status == SessionStatus.RUNNING } ?: return
        record = active.copy(activeDurationMs = activeDurationNow())
        runningSinceBootMs = null // Freeze elapsed work before durable PAUSE intent/recovery.
        pausedSinceBootMs = clock.elapsedRealtimeMs()
        pendingOperation = WearExternalOperation.PAUSE
        persistSnapshotAndRecord()
        if (healthExerciseOwned) health.pause()
        record = record!!.copy(status = SessionStatus.PAUSED)
        runningSinceBootMs = null
        pendingOperation = null
        persistSnapshotAndRecord()
        haptics.play(Cue.PAUSE)
    }

    override suspend fun resume() = mutex.withLock {
        val paused = record?.takeIf { it.status == SessionStatus.PAUSED } ?: return
        pendingOperation = WearExternalOperation.RESUME
        persistSnapshotAndRecord()
        if (healthExerciseOwned) health.resume()
        record = paused.copy(
            status = SessionStatus.RUNNING,
            pausedDurationMs = paused.pausedDurationMs + (clock.elapsedRealtimeMs() - (pausedSinceBootMs ?: clock.elapsedRealtimeMs())).coerceAtLeast(0),
        )
        runningSinceBootMs = clock.elapsedRealtimeMs()
        heartRateAcceptFromBootMs = runningSinceBootMs
        pausedSinceBootMs = null
        pendingOperation = null
        persistSnapshotAndRecord()
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
                val active = record?.takeIf { it.status == SessionStatus.RUNNING && healthExerciseOwned } ?: return
                if (update.bpm !in MIN_HEART_RATE..MAX_HEART_RATE) return
                val nowElapsed = clock.elapsedRealtimeMs()
                val lowerBound = heartRateAcceptFromBootMs ?: nowElapsed
                if (update.timeSinceBootMs !in lowerBound..nowElapsed) return
                val epoch = try {
                    Math.addExact(Math.subtractExact(clock.epochMillis(), nowElapsed), update.timeSinceBootMs)
                } catch (_: ArithmeticException) { return }
                if (epoch !in (active.startEpochMillis ?: epoch)..clock.epochMillis()) return
                val sample = HeartRateSample(
                    epochMillis = epoch,
                    bpm = update.bpm,
                    accuracy = SampleAccuracy.HIGH,
                )
                record = active.copy(heartRateSamples = active.heartRateSamples + sample)
                currentHeartRateBpm = update.bpm
                samplesSinceStore += 1
                // Room owns samples; no synchronous growing SharedPreferences history write on callback.
                if (samplesSinceStore >= SAMPLE_BATCH_COUNT || clock.elapsedRealtimeMs() - lastStoredSampleAtBootMs >= SAMPLE_BATCH_INTERVAL_MS) {
                    persistSnapshotAndRecord()
                } else {
                    publishState()
                }
            }
        }
    }

    override suspend fun recover(currentBootId: String): WearSessionRecovery = mutex.withLock {
        val snapshot = snapshots.load() ?: return@withLock WearSessionRecovery.NO_SESSION
        // Snapshot omits growing HR history; merge Room's durable aggregate before any upsert.
        val durable = records.get(snapshot.record.id)
        record = snapshot.record.copy(heartRateSamples = durable?.heartRateSamples ?: snapshot.record.heartRateSamples)
        runningSinceBootMs = snapshot.runningSinceBootMs
        nextReminderAtActiveMs = snapshot.nextReminderAtActiveMs
        healthExerciseOwned = snapshot.healthExerciseOwned
        pendingOperation = snapshot.pendingOperation
        pausedSinceBootMs = snapshot.pausedSinceBootMs
        intendedTerminalStatus = snapshot.intendedTerminalStatus
        heartRateAcceptFromBootMs = snapshot.heartRateAcceptFromBootMs
        if (record!!.status in TERMINAL_STATUSES) {
            repairTerminalDurability(snapshotBootId = currentBootId)
            publishState()
            return@withLock WearSessionRecovery.TERMINAL
        }
        if (snapshot.bootId != currentBootId && record!!.status in ACTIVE_STATUSES) {
            record = record!!.copy(status = SessionStatus.INTERRUPTED, endEpochMillis = clock.epochMillis())
            runningSinceBootMs = null
            healthExerciseOwned = false
            persistSnapshotAndRecord(currentBootId)
            return@withLock WearSessionRecovery.TERMINAL
        }
        if (record!!.status == SessionStatus.STARTING || pendingOperation == WearExternalOperation.START) {
            applyStartResult(health.start())
            record = record!!.copy(status = SessionStatus.RUNNING)
            runningSinceBootMs = clock.elapsedRealtimeMs()
            heartRateAcceptFromBootMs = runningSinceBootMs
            pendingOperation = null
            persistSnapshotAndRecord(currentBootId)
            return@withLock WearSessionRecovery.ACTIVE
        }
        if (record!!.status == SessionStatus.COMPLETING || pendingOperation == WearExternalOperation.END) {
            if (healthExerciseOwned) applyStartResult(health.reattach())
            if (healthExerciseOwned) health.end()
            healthExerciseOwned = false
            pendingOperation = null
            val terminalStatus = intendedTerminalStatus ?: SessionStatus.COMPLETED
            record = record!!.copy(status = terminalStatus, endEpochMillis = clock.epochMillis())
            if (terminalStatus == SessionStatus.COMPLETED) persistCompletedWithOutbox(currentBootId) else persistSnapshotAndRecord(currentBootId)
            return@withLock WearSessionRecovery.TERMINAL
        }
        if (record!!.status == SessionStatus.RUNNING && runningSinceBootMs != null) {
            record = record!!.copy(activeDurationMs = activeDurationNow())
            runningSinceBootMs = clock.elapsedRealtimeMs()
        }
        if (record!!.status in ACTIVE_STATUSES && healthExerciseOwned) {
            applyStartResult(health.reattach())
        }
        if (record!!.status == SessionStatus.PAUSED && pendingOperation == null && healthExerciseOwned) {
            // Reassert the paused external state after a process restart, with a durable retry marker.
            pendingOperation = WearExternalOperation.PAUSE
            persistSnapshotAndRecord(currentBootId)
        }
        when (pendingOperation) {
            WearExternalOperation.PAUSE -> {
                val alreadyPaused = record!!.status == SessionStatus.PAUSED
                if (healthExerciseOwned) health.pause()
                record = record!!.copy(status = SessionStatus.PAUSED, activeDurationMs = activeDurationNow())
                runningSinceBootMs = null
                if (!alreadyPaused || pausedSinceBootMs == null) pausedSinceBootMs = clock.elapsedRealtimeMs()
                pendingOperation = null
                persistSnapshotAndRecord(currentBootId)
            }
            WearExternalOperation.RESUME -> {
                if (healthExerciseOwned) health.resume()
                val paused = record!!
                record = paused.copy(
                    status = SessionStatus.RUNNING,
                    pausedDurationMs = paused.pausedDurationMs + (clock.elapsedRealtimeMs() - (pausedSinceBootMs ?: clock.elapsedRealtimeMs())).coerceAtLeast(0),
                )
                runningSinceBootMs = clock.elapsedRealtimeMs()
                heartRateAcceptFromBootMs = runningSinceBootMs
                pausedSinceBootMs = null
                pendingOperation = null
                persistSnapshotAndRecord(currentBootId)
            }
            else -> persistSnapshotAndRecord(currentBootId)
        }
        WearSessionRecovery.ACTIVE
    }

    private suspend fun finishLocked(cancelled: Boolean, activeDurationMs: Long = activeDurationNow()) {
        val active = record?.takeIf { it.status in ACTIVE_STATUSES } ?: return
        record = active.copy(status = SessionStatus.COMPLETING, activeDurationMs = activeDurationMs)
        runningSinceBootMs = null
        pendingOperation = WearExternalOperation.END
        intendedTerminalStatus = if (cancelled) SessionStatus.CANCELLED else SessionStatus.COMPLETED
        persistSnapshotAndRecord()
        if (healthExerciseOwned) health.end()
        healthExerciseOwned = false
        pendingOperation = null
        val terminalStatus = if (cancelled) SessionStatus.CANCELLED else SessionStatus.COMPLETED
        record = record!!.copy(status = terminalStatus, endEpochMillis = clock.epochMillis())
        if (cancelled) persistSnapshotAndRecord() else persistCompletedWithOutbox()
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
                pendingOperation = pendingOperation,
                pausedSinceBootMs = pausedSinceBootMs,
                intendedTerminalStatus = intendedTerminalStatus,
                heartRateAcceptFromBootMs = heartRateAcceptFromBootMs,
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

    private fun applyStartResult(result: WearHealthStartResult) {
        when (result) {
            WearHealthStartResult.Started -> healthExerciseOwned = true
            is WearHealthStartResult.StartedWithoutUpdates -> { healthExerciseOwned = true; heartRateUnavailableReason = result.reason.name }
            is WearHealthStartResult.DurationOnly -> { healthExerciseOwned = false; heartRateUnavailableReason = result.reason.name }
        }
    }

    private suspend fun persistCompletedWithOutbox(snapshotBootId: String = bootId()) {
        record = record!!.copy(revision = record!!.revision + 1)
        saveSnapshotOnly(snapshotBootId)
        val complete = record!!
        records.completeWithOutbox(complete, "${complete.id}:${complete.revision}", complete.toOutboxPayload())
        lastStoredSampleAtBootMs = clock.elapsedRealtimeMs()
        samplesSinceStore = 0
        publishState()
    }

    private suspend fun repairTerminalDurability(snapshotBootId: String) {
        val terminal = record!!
        if (terminal.status == SessionStatus.COMPLETED) {
            records.completeWithOutbox(terminal, "${terminal.id}:${terminal.revision}", terminal.toOutboxPayload())
        } else {
            records.upsert(terminal)
        }
        // Keep the exact snapshot revision; terminal recovery must never stale the outbox.
        saveSnapshotOnly(snapshotBootId)
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val SNAPSHOT_INTERVAL_MS = 60_000L
        const val SAMPLE_BATCH_INTERVAL_MS = 30_000L
        const val SAMPLE_BATCH_COUNT = 20
        const val MIN_HEART_RATE = 20.0
        const val MAX_HEART_RATE = 240.0
        val ACTIVE_STATUSES = setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
        val TERMINAL_STATUSES = setOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.INTERRUPTED)
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
    override suspend fun completeWithOutbox(record: SessionRecord, eventId: String, payload: String) =
        repository.completeWithOutbox(record, eventId, payload)
}
