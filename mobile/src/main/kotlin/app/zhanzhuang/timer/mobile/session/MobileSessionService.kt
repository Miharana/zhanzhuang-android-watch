package app.zhanzhuang.timer.mobile.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.room.Room
import app.zhanzhuang.timer.mobile.data.MobileDatabase
import app.zhanzhuang.timer.mobile.data.MobileSessionRepository
import app.zhanzhuang.timer.mobile.health.HealthSyncWorker
import app.zhanzhuang.timer.R
import app.zhanzhuang.timer.model.SessionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class MobileSessionServiceDependencies(
    val controllerFactory: (Context) -> MobileSessionController,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val hostFactory: ((MobileSessionService) -> MobileForegroundServiceHost)? = null,
)

open class MobileSessionService : Service() {
    private lateinit var serviceScope: CoroutineScope
    private lateinit var runtime: MobileSessionServiceRuntime
    private val actionMutex = Mutex()
    private var tickJob: Job? = null
    private var latestStartId = 0
    internal var lastActionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val dependencies = testDependencies ?: defaultDependencies()
        serviceScope = CoroutineScope(SupervisorJob() + dependencies.dispatcher)
        val controller = dependencies.controllerFactory(applicationContext)
        serviceScope.launch { controller.state.collect(MobileSessionUiBridge::publish) }
        serviceScope.launch {
            try {
                HealthSyncWorker.reconcile(applicationContext)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Reconciliation is opportunistic; timer recovery must remain available offline.
            }
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        runtime = MobileSessionServiceRuntime(
            controller,
            dependencies.hostFactory?.invoke(this) ?: foregroundServiceHost(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = maxOf(latestStartId, startId)
        lastActionJob = serviceScope.launch {
            actionMutex.withLock {
                when (intent?.action) {
                    ACTION_START -> {
                        val config = SessionConfig(
                            intent.getIntExtra(EXTRA_DURATION_MINUTES, 30),
                            intent.getIntExtra(EXTRA_INTERVAL_MINUTES, 10),
                        )
                        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                        if (sessionId == null) {
                            if (!runtime.recover(tearDownWhenInactive = false, startId = startId)) runtime.start(config, null, startId)
                        } else {
                            // Data Layer fallback has already durably reserved this exact ID.
                            runtime.start(config, sessionId, startId)
                        }
                    }
                    ACTION_PAUSE -> {
                        runtime.recover(startId = startId)
                        runtime.pause(startId)
                    }
                    ACTION_RESUME -> {
                        runtime.recover(startId = startId)
                        runtime.resume(startId)
                    }
                    ACTION_FINISH -> {
                        runtime.recover(startId = startId)
                        runtime.finish(intent.getBooleanExtra(EXTRA_CANCELLED, false), startId)
                    }
                    null -> runtime.recover(startId = startId)
                    else -> runtime.recover(startId = startId)
                }
            }
        }
        return when (intent?.action) {
            null, ACTION_START, ACTION_PAUSE, ACTION_RESUME -> START_STICKY
            else -> START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun foregroundServiceHost() = object : MobileForegroundServiceHost {
            override fun startTicker() {
                if (tickJob?.isActive == true) return
                tickJob = serviceScope.launch {
                    while (isActive) {
                        runtime.tick(latestStartId)
                        delay(TICK_INTERVAL_MS)
                    }
                }
            }

            override fun stopTicker() {
                tickJob?.cancel()
                tickJob = null
            }

            override fun removeForegroundNotification() {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }

            override fun stopService(startId: Int?): Boolean {
                return if (startId == null) {
                    this@MobileSessionService.stopSelf()
                    true
                } else {
                    requestStop(startId)
                }
            }
    }

    protected open fun requestStop(startId: Int): Boolean = stopSelfResult(startId)

    private fun defaultDependencies() = MobileSessionServiceDependencies(
        controllerFactory = { context ->
            DefaultMobileSessionController(
                repository = MobileSessionRepository(
                    Room.databaseBuilder(context, MobileDatabase::class.java, DATABASE_NAME)
                        .addMigrations(MobileDatabase.MIGRATION_1_2)
                        .build(),
                ),
                elapsedClock = ElapsedClock { SystemClock.elapsedRealtime() },
                wallClock = WallClock { System.currentTimeMillis() },
                haptics = AndroidMobileHaptics(context.getSystemService(Vibrator::class.java)),
                healthSyncScheduler = HealthSyncScheduler { record ->
                    if (HealthSyncWorker.enqueue(context, record)) {
                        HealthSyncEnqueueResult.Enqueued
                    } else {
                        HealthSyncEnqueueResult.NotEligible
                    }
                },
            )
        },
    )

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_mobile), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(getString(R.string.notification_channel_mobile))
        .setContentText(getString(R.string.notification_active_phone))
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
        const val EXTRA_SESSION_ID = "session_id"

        internal var testDependencies: MobileSessionServiceDependencies? = null

        fun start(context: Context, config: SessionConfig, sessionId: String? = null) {
            val intent = Intent(context, MobileSessionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DURATION_MINUTES, config.durationMinutes)
                .putExtra(EXTRA_INTERVAL_MINUTES, config.intervalMinutes)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            context.startForegroundService(intent)
        }

        fun command(context: Context, action: String, cancelled: Boolean = false) {
            val intent = Intent(context, MobileSessionService::class.java)
                .setAction(action)
                .putExtra(EXTRA_CANCELLED, cancelled)
            context.startForegroundService(intent)
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
