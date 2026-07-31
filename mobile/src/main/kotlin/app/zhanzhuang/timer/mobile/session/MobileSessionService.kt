package app.zhanzhuang.timer.mobile.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.room.Room
import app.zhanzhuang.timer.mobile.data.MobileDatabase
import app.zhanzhuang.timer.mobile.data.MobileSessionRepository
import app.zhanzhuang.timer.model.SessionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MobileSessionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var controller: MobileSessionController

    override fun onCreate() {
        super.onCreate()
        controller = DefaultMobileSessionController(
            repository = MobileSessionRepository(
                Room.databaseBuilder(applicationContext, MobileDatabase::class.java, DATABASE_NAME).build(),
            ),
            elapsedClock = ElapsedClock { SystemClock.elapsedRealtime() },
            wallClock = WallClock { System.currentTimeMillis() },
            haptics = AndroidMobileHaptics(getSystemService(Vibrator::class.java)),
        )
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        serviceScope.launch {
            while (isActive) {
                controller.tick()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> serviceScope.launch {
                controller.start(
                    SessionConfig(
                        intent.getIntExtra(EXTRA_DURATION_MINUTES, 30),
                        intent.getIntExtra(EXTRA_INTERVAL_MINUTES, 10),
                    ),
                )
            }
            ACTION_PAUSE -> serviceScope.launch { controller.pause() }
            ACTION_RESUME -> serviceScope.launch { controller.resume() }
            ACTION_FINISH -> serviceScope.launch { controller.finish(intent.getBooleanExtra(EXTRA_CANCELLED, false)) }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Standing meditation timer", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Standing meditation timer")
        .setContentText("A session is active on this phone")
        .setOngoing(true)
        .build()

    companion object {
        private const val DATABASE_NAME = "zhan_zhuang.db"
        private const val CHANNEL_ID = "standing_timer"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_INTERVAL_MS = 1_000L
        const val ACTION_START = "app.zhanzhuang.timer.mobile.action.START"
        const val ACTION_PAUSE = "app.zhanzhuang.timer.mobile.action.PAUSE"
        const val ACTION_RESUME = "app.zhanzhuang.timer.mobile.action.RESUME"
        const val ACTION_FINISH = "app.zhanzhuang.timer.mobile.action.FINISH"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val EXTRA_INTERVAL_MINUTES = "interval_minutes"
        const val EXTRA_CANCELLED = "cancelled"

        fun start(context: Context, config: SessionConfig) {
            val intent = Intent(context, MobileSessionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DURATION_MINUTES, config.durationMinutes)
                .putExtra(EXTRA_INTERVAL_MINUTES, config.intervalMinutes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}

private class AndroidMobileHaptics(private val vibrator: Vibrator?) : MobileHaptics {
    override fun start() = vibrate(80)
    override fun interval() = vibrate(120)
    override fun pauseResume() = vibrate(50)
    override fun completion() = vibrate(350)

    private fun vibrate(milliseconds: Long) {
        vibrator?.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
