package app.zhanzhuang.timer.wear.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import app.zhanzhuang.timer.wear.R

/** Builds one truthful, non-dismissable notification and registers it as a Wear ongoing activity. */
class OngoingSessionNotification(private val context: Context) {
    fun build(state: WearSessionUiState?, touchIntent: PendingIntent): android.app.Notification {
        ensureChannel()
        val text = when (state?.record?.status) {
            null -> context.getString(R.string.notification_recovering)
            app.zhanzhuang.timer.model.SessionStatus.STARTING -> context.getString(R.string.notification_preparing)
            app.zhanzhuang.timer.model.SessionStatus.RUNNING -> context.getString(R.string.notification_running, formatRemaining(state.remainingMs))
            app.zhanzhuang.timer.model.SessionStatus.PAUSED -> context.getString(R.string.notification_paused)
            app.zhanzhuang.timer.model.SessionStatus.COMPLETING -> context.getString(R.string.notification_finishing)
            app.zhanzhuang.timer.model.SessionStatus.COMPLETED -> context.getString(R.string.notification_complete)
            app.zhanzhuang.timer.model.SessionStatus.CANCELLED -> context.getString(R.string.notification_cancelled)
            app.zhanzhuang.timer.model.SessionStatus.INTERRUPTED -> context.getString(R.string.notification_interrupted)
            else -> context.getString(R.string.notification_unavailable)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(state?.record?.status in ACTIVE_NOTIFICATION_STATUSES)
            .setOnlyAlertOnce(true)
            .setContentIntent(touchIntent)
        // Task 7 will replace this temporary STATUS tap target with a session screen.
        // Never attach an ongoing-activity extension for an empty or terminal state.
        if (state?.record?.status in ACTIVE_NOTIFICATION_STATUSES) {
            OngoingActivity.Builder(context, NOTIFICATION_ID, builder).build().apply(context)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_wear), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun formatRemaining(remainingMs: Long): String {
        val seconds = (remainingMs.coerceAtLeast(0) + 999) / 1_000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    companion object {
        const val CHANNEL_ID = "wear_session"
        const val NOTIFICATION_ID = 501
        private val ACTIVE_NOTIFICATION_STATUSES = setOf(
            app.zhanzhuang.timer.model.SessionStatus.STARTING,
            app.zhanzhuang.timer.model.SessionStatus.RUNNING,
            app.zhanzhuang.timer.model.SessionStatus.PAUSED,
            app.zhanzhuang.timer.model.SessionStatus.COMPLETING,
        )
    }
}
