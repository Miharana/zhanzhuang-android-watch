package app.zhanzhuang.timer.wear.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-enters the service only to recover durable state; it never creates a session itself. */
class SessionRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        app.zhanzhuang.timer.wear.sync.WearCompletionSyncWorker.enqueue(context)
        context.startForegroundService(
            Intent(context, WearSessionService::class.java).setAction(WearSessionService.ACTION_STATUS),
        )
    }
}
