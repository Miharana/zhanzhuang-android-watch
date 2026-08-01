package app.zhanzhuang.timer.wear.session

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Vibrator
import androidx.health.services.client.HealthServices
import androidx.room.Room
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.wear.data.WearDatabase
import app.zhanzhuang.timer.wear.data.WearSessionRepository
import app.zhanzhuang.timer.wear.health.AndroidWearHealthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

/** Health foreground service. Only [startIntent] can create a new session; restart paths only recover. */
class WearSessionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var controller: WearSessionControllerImpl
    private lateinit var notification: OngoingSessionNotification
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(applicationContext, WearDatabase::class.java, "wear-sessions.db").build()
        val health = AndroidWearHealthClient(HealthServices.getClient(applicationContext).exerciseClient)
        controller = WearSessionControllerImpl(
            records = RepositoryRecordStore(WearSessionRepository(database)),
            snapshots = SharedPreferencesSessionSnapshotStore(
                getSharedPreferences("wear_session", Context.MODE_PRIVATE),
            ),
            health = health,
            haptics = AndroidHapticCuePlayer(getSystemService(Vibrator::class.java)),
            bootId = { currentBootId(applicationContext) },
        )
        notification = OngoingSessionNotification(applicationContext)
        serviceScope.launch {
            controller.state.collect { state ->
                startForeground(OngoingSessionNotification.NOTIFICATION_ID, notification.build(state, statusIntent()))
            }
        }
        serviceScope.launch {
            health.updates.collect { update -> controller.acceptHealthUpdate(update) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The foreground health notification is posted synchronously before any asynchronous work.
        startForeground(OngoingSessionNotification.NOTIFICATION_ID, notification.build(controller.state.value, statusIntent()))
        when (val command = commandFrom(intent)) {
            is Command.Start -> serviceScope.launch {
                controller.start(command.config, command.sessionId)
                startTicker(startId)
            }
            Command.Finish -> serviceScope.launch {
                try {
                    controller.finish()
                } finally {
                    ticker?.cancel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(startId) // Never stops a newer service start.
                }
            }
            Command.Cancel -> serviceScope.launch {
                try {
                    controller.finish(cancelled = true)
                } finally {
                    ticker?.cancel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(startId)
                }
            }
            Command.Status, null -> serviceScope.launch {
                controller.recover(currentBootId(applicationContext))
                startTicker(startId)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTicker(startId: Int) {
        ticker?.cancel()
        ticker = serviceScope.launch {
            while (true) {
                delay(TICK_MS)
                controller.tick()
                if (controller.state.value.record?.status in TERMINAL_STATUSES) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(startId) // An old ticker cannot stop a newer start.
                    return@launch
                }
            }
        }
    }

    private fun statusIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, WearSessionService::class.java).setAction(ACTION_STATUS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    sealed interface Command {
        data class Start(val config: SessionConfig, val sessionId: String) : Command
        data object Finish : Command
        data object Cancel : Command
        data object Status : Command
    }

    companion object {
        const val ACTION_START = "app.zhanzhuang.timer.wear.session.START"
        const val ACTION_FINISH = "app.zhanzhuang.timer.wear.session.FINISH"
        const val ACTION_CANCEL = "app.zhanzhuang.timer.wear.session.CANCEL"
        const val ACTION_STATUS = "app.zhanzhuang.timer.wear.session.STATUS"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val EXTRA_INTERVAL_MINUTES = "interval_minutes"
        private const val TICK_MS = 1_000L
        private val TERMINAL_STATUSES = setOf(
            app.zhanzhuang.timer.model.SessionStatus.COMPLETED,
            app.zhanzhuang.timer.model.SessionStatus.CANCELLED,
            app.zhanzhuang.timer.model.SessionStatus.INTERRUPTED,
        )

        /** This factory is the explicit-user-action entry point; callers use ContextCompat.startForegroundService. */
        fun startIntent(context: Context, config: SessionConfig, sessionId: String): Intent =
            Intent(context, WearSessionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_DURATION_MINUTES, config.durationMinutes)
                .putExtra(EXTRA_INTERVAL_MINUTES, config.intervalMinutes)

        fun commandFrom(intent: Intent?): Command? = when (intent?.action) {
            ACTION_START -> runCatching {
                val sessionId = requireNotNull(intent.getStringExtra(EXTRA_SESSION_ID))
                Command.Start(
                    SessionConfig(
                        requireNotNull(intent.getIntExtra(EXTRA_DURATION_MINUTES, -1).takeIf { it > 0 }),
                        requireNotNull(intent.getIntExtra(EXTRA_INTERVAL_MINUTES, -1).takeIf { it > 0 }),
                    ),
                    sessionId,
                )
            }.getOrNull()
            ACTION_FINISH -> Command.Finish
            ACTION_CANCEL -> Command.Cancel
            ACTION_STATUS -> Command.Status
            else -> null
        }

        fun currentBootId(context: Context): String = runCatching {
            File("/proc/sys/kernel/random/boot_id").readText().trim().takeIf { it.isNotEmpty() }
                ?: error("empty kernel boot id")
        }.getOrElse {
            // A process-unique fallback is conservative: it interrupts rather than fabricating elapsed time.
            "unavailable:${android.os.Process.myPid()}:${android.os.SystemClock.elapsedRealtime()}"
        }
    }
}
