package app.zhanzhuang.timer.mobile.health

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.room.Room
import app.zhanzhuang.timer.mobile.data.MobileDatabase
import app.zhanzhuang.timer.mobile.data.MobileSessionRepository
import app.zhanzhuang.timer.mobile.data.SessionRepository
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SyncState
import java.util.concurrent.TimeUnit

sealed interface HealthSyncOutcome {
    data class Synced(val sessionWritten: Boolean, val heartRateWritten: Boolean) : HealthSyncOutcome
    data class PermissionMissing(val permissions: Set<String>) : HealthSyncOutcome
    data class Unavailable(val reason: String) : HealthSyncOutcome
    data class Skipped(val reason: String) : HealthSyncOutcome
    data object Retry : HealthSyncOutcome
    data class Failed(val reason: String) : HealthSyncOutcome
}

class HealthSyncWorker(
    private val gateway: HealthConnectGateway,
) {
    suspend fun sync(record: SessionRecord): HealthSyncOutcome {
        if (record.status != SessionStatus.COMPLETED) {
            return HealthSyncOutcome.Skipped("Only completed sessions can be exported")
        }
        if (record.activeDurationMs < MIN_EXPORT_DURATION_MILLIS) {
            return HealthSyncOutcome.Skipped("Sessions shorter than one minute remain local")
        }
        if (record.startEpochMillis == null || record.endEpochMillis == null) {
            return HealthSyncOutcome.Failed("Completed session has no time interval")
        }

        return when (gateway.availability()) {
            HealthAvailability.UNAVAILABLE -> HealthSyncOutcome.Unavailable("Health Connect is unavailable")
            HealthAvailability.AVAILABLE,
            HealthAvailability.MINDFULNESS_UNSUPPORTED,
            -> gateway.write(record).toOutcome()
        }
    }

    private fun HealthWriteResult.toOutcome(): HealthSyncOutcome = when (this) {
        is HealthWriteResult.Success -> HealthSyncOutcome.Synced(sessionWritten, heartRateWritten)
        is HealthWriteResult.PermissionMissing -> HealthSyncOutcome.PermissionMissing(permissions)
        is HealthWriteResult.Unavailable -> HealthSyncOutcome.Unavailable(reason)
        is HealthWriteResult.Retryable -> HealthSyncOutcome.Retry
        is HealthWriteResult.PermanentFailure -> HealthSyncOutcome.Failed(reason)
    }

    companion object {
        const val SESSION_ID_INPUT_KEY = "session_id"
        const val MIN_EXPORT_DURATION_MILLIS = 60_000L

        fun nextHealthConnectState(
            record: SessionRecord,
            outcome: HealthSyncOutcome,
        ): SyncState? = when (outcome) {
            is HealthSyncOutcome.Synced -> when {
                !outcome.sessionWritten -> SyncState.FAILED
                AndroidHealthConnectGateway.exportableHeartRateSamples(record).isNotEmpty() &&
                    !outcome.heartRateWritten -> SyncState.FAILED
                else -> SyncState.SYNCED
            }
            is HealthSyncOutcome.PermissionMissing,
            is HealthSyncOutcome.Unavailable,
            is HealthSyncOutcome.Failed,
            -> SyncState.FAILED
            HealthSyncOutcome.Retry -> SyncState.PENDING
            is HealthSyncOutcome.Skipped -> null
        }

        fun recordAfterOutcome(
            record: SessionRecord,
            outcome: HealthSyncOutcome,
        ): SessionRecord? = nextHealthConnectState(record, outcome)
            ?.takeIf { it != record.healthConnectState }
            ?.let { state -> record.copy(revision = record.revision + 1, healthConnectState = state) }

        fun enqueue(context: Context, record: SessionRecord): Boolean {
            if (record.status != SessionStatus.COMPLETED || record.activeDurationMs < MIN_EXPORT_DURATION_MILLIS) {
                return false
            }
            val request = OneTimeWorkRequestBuilder<HealthConnectExportWorker>()
                .setInputData(Data.Builder().putString(SESSION_ID_INPUT_KEY, record.id).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "health-sync:${record.id}",
                ExistingWorkPolicy.REPLACE,
                request,
            )
            return true
        }

        suspend fun reconcile(context: Context) {
            val repository = HealthSyncWorkerDependencies.sessionRepository?.invoke(context) ?: return
            reconciliationCandidates(repository.pendingHealthSyncSessions()).forEach { enqueue(context, it) }
        }

        fun reconciliationCandidates(records: List<SessionRecord>): List<SessionRecord> = records.filter {
            it.status == SessionStatus.COMPLETED &&
                it.healthConnectState == SyncState.PENDING &&
                it.activeDurationMs >= MIN_EXPORT_DURATION_MILLIS
        }
    }
}

class HealthConnectExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(HealthSyncWorker.SESSION_ID_INPUT_KEY) ?: return Result.failure()
        val repositoryFactory = HealthSyncWorkerDependencies.sessionRepository ?: return Result.failure()
        val repository = repositoryFactory(applicationContext)
        val record = repository.get(sessionId) ?: return Result.success()
        val outcome = HealthSyncWorker(HealthSyncWorkerDependencies.gateway(applicationContext)).sync(record)
        HealthSyncWorker.recordAfterOutcome(record, outcome)?.let { updated ->
            repository.upsert(updated)
        }
        return when (outcome) {
            HealthSyncOutcome.Retry -> Result.retry()
            else -> Result.success()
        }
    }
}

object HealthSyncWorkerDependencies {
    var sessionRepository: ((Context) -> SessionRepository)? = { context ->
        MobileSessionRepository(
            Room.databaseBuilder(context.applicationContext, MobileDatabase::class.java, DATABASE_NAME)
                .addMigrations(MobileDatabase.MIGRATION_1_2)
                .build(),
        )
    }
    var gateway: (Context) -> HealthConnectGateway = ::AndroidHealthConnectGateway

    private const val DATABASE_NAME = "zhan_zhuang.db"
}
