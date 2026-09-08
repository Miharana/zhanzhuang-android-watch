package app.zhanzhuang.timer.wear.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.room.Room
import app.zhanzhuang.timer.wear.data.WearDatabase
import app.zhanzhuang.timer.wear.data.WearSessionRepository
import java.util.concurrent.TimeUnit

enum class WearCompletionDelivery { DONE, RETRY }

object WearCompletionRetryPolicy {
    fun resultFor(sent: Boolean): WearCompletionDelivery = if (sent) WearCompletionDelivery.DONE else WearCompletionDelivery.RETRY
}

/** Durable trigger for every completion, independent of listener lifetime. */
class WearCompletionSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = WearSessionRepository(
            Room.databaseBuilder(applicationContext, WearDatabase::class.java, DATABASE_NAME).build(),
        )
        val coordinator = WearSyncCoordinator(
            outbox = WearRepositoryCompletionOutbox(repository),
            transport = WearAndroidDataLayerTransport(applicationContext),
        )
        coordinator.resendCompleted()
        return when (WearCompletionRetryPolicy.resultFor(repository.pendingOutbox().isEmpty())) {
            WearCompletionDelivery.DONE -> Result.success()
            WearCompletionDelivery.RETRY -> Result.retry()
        }
    }

    companion object {
        private const val DATABASE_NAME = "wear-sessions.db"
        private const val UNIQUE_WORK = "wear-completion-sync"

        fun enqueue(context: Context, eventId: String = "all") {
            val request = OneTimeWorkRequestBuilder<WearCompletionSyncWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "$UNIQUE_WORK:$eventId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
