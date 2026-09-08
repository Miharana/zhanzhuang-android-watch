package app.zhanzhuang.timer.wear.session

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.os.Vibrator
import androidx.health.services.client.HealthServices
import androidx.core.app.ServiceCompat
import androidx.room.Room
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRuntime
import app.zhanzhuang.timer.wear.data.WearDatabase
import app.zhanzhuang.timer.wear.data.WearSessionRepository
import app.zhanzhuang.timer.wear.health.AndroidWearHealthClient
import app.zhanzhuang.timer.wear.health.PermissionAwareWearHealthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Health foreground service. Only [startIntent] can create a new session; restart paths only recover. */
class WearSessionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var controller: WearSessionControllerImpl
    private lateinit var notification: OngoingSessionNotification
    private var ticker: Job? = null
    private var runtimePublishJob: Job? = null
    private var lastRuntimePublishElapsedMs = Long.MIN_VALUE
    private lateinit var commands: WearSessionCommandProcessor
    private val requests = Channel<ServiceRequest>(Channel.UNLIMITED)
    private val generation = AtomicLong(0)

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(applicationContext, WearDatabase::class.java, "wear-sessions.db").build()
        val health = PermissionAwareWearHealthClient(
            delegate = AndroidWearHealthClient(HealthServices.getClient(applicationContext).exerciseClient),
            permissionGranted = { WearForegroundPolicy.hasHeartRatePermission(applicationContext) },
        )
        controller = WearSessionControllerImpl(
            records = RepositoryRecordStore(WearSessionRepository(database)),
            snapshots = SharedPreferencesSessionSnapshotStore(
                getSharedPreferences("wear_session", Context.MODE_PRIVATE),
            ),
            health = health,
            haptics = AndroidHapticCuePlayer(getSystemService(Vibrator::class.java)),
            bootId = { currentBootId(applicationContext) },
            completionSyncScheduler = { eventId -> app.zhanzhuang.timer.wear.sync.WearCompletionSyncWorker.enqueue(applicationContext, eventId) },
        )
        notification = OngoingSessionNotification(applicationContext)
        commands = WearSessionCommandProcessor(controller)
        serviceScope.launch {
            controller.state.collect { state -> WearSessionUiBridge.publish(state) }
        }
        serviceScope.launch {
            health.updates.collect { update -> controller.acceptHealthUpdate(update) }
        }
        serviceScope.launch {
            for (request in requests) {
                try {
                    processRequest(request)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The controller has already durably recorded its pending external operation.
                    // Keep this actor alive so STATUS (or the next explicit action) can retry recovery.
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = commandFrom(intent) ?: Command.Status
        val preferences = getSharedPreferences(WearSessionRecoveryPolicy.PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (command == Command.Status && !WearSessionRecoveryPolicy.hasSnapshot(preferences)) {
            // Defensive guard for stale/system callers. A clean install has no
            // recoverable work and must not enter the foreground-service path.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val commandGeneration = generation.incrementAndGet()
        if (command != Command.Status || WearSessionRecoveryPolicy.statusRequiresForeground(preferences)) {
            // This satisfies the FGS deadline for active work; the recovery-only
            // notification has no OngoingActivity extension.
            promoteToForeground()
        }
        requests.trySend(ServiceRequest.Command(command, intent?.getStringExtra(EXTRA_SESSION_ID), intent?.getStringExtra(EXTRA_REQUEST_ID), startId, commandGeneration))
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        runtimePublishJob?.cancel()
        requests.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTicker(startId: Int, tickerGeneration: Long) {
        if (tickerGeneration != generation.get()) return
        ticker?.cancel()
        ticker = serviceScope.launch {
            while (true) {
                delay(TICK_MS)
                requests.trySend(ServiceRequest.Tick(startId, tickerGeneration))
            }
        }
    }

    private fun stopIfCurrent(startId: Int, commandGeneration: Long) {
        if (commandGeneration != generation.get()) return
        if (stopSelfResult(startId)) {
            ticker?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private suspend fun processRequest(request: ServiceRequest) {
        when (request) {
            is ServiceRequest.Command -> when (commands.process(request.command, currentBootId(applicationContext), request.expectedSessionId)) {
                ServiceCommandResult.ACTIVE -> {
                    publishForeground(request.generation)
                    startTicker(request.startId, request.generation)
                    scheduleRuntimePublish(force = true)
                    app.zhanzhuang.timer.wear.sync.WearServiceCommandGateway.complete(
                        request.requestId,
                        controller.state.value.record?.takeIf { request.expectedSessionId == null || it.id == request.expectedSessionId },
                    )
                }
                ServiceCommandResult.STOP -> {
                    app.zhanzhuang.timer.wear.sync.WearServiceCommandGateway.complete(
                        request.requestId,
                        controller.state.value.record?.takeIf { request.expectedSessionId == null || it.id == request.expectedSessionId },
                    )
                    stopIfCurrent(request.startId, request.generation)
                }
                ServiceCommandResult.REJECTED -> {
                    // A stale remote command must never tear down a different valid session.
                    if (controller.state.value.record?.status in ACTIVE_STATUSES) {
                        publishForeground(request.generation)
                        startTicker(request.startId, request.generation)
                    } else {
                        stopIfCurrent(request.startId, request.generation)
                    }
                    app.zhanzhuang.timer.wear.sync.WearServiceCommandGateway.complete(request.requestId, null)
                }
            }
            is ServiceRequest.Tick -> if (request.generation == generation.get()) {
                controller.tick()
                publishForeground(request.generation)
                scheduleRuntimePublish(force = false)
                if (controller.state.value.record?.status in TERMINAL_STATUSES) {
                    publishTerminalState()
                    stopIfCurrent(request.startId, request.generation)
                }
            }
        }
    }

    private fun publishForeground(requestGeneration: Long) {
        if (requestGeneration != generation.get() || controller.state.value.record?.status !in ACTIVE_STATUSES) return
        promoteToForeground()
    }

    /**
     * The Wear foreground service, not the phone UI, samples this runtime
     * presentation. Five-second state messages keep the companion countdown
     * truthful without a per-second radio wakeup.
     */
    private fun scheduleRuntimePublish(force: Boolean) {
        val state = controller.state.value
        val record = state.record?.takeIf {
            it.owner == SessionOwner.WEAR && it.status in ACTIVE_STATUSES
        } ?: return
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && elapsed - lastRuntimePublishElapsedMs < RUNTIME_PUBLISH_INTERVAL_MS) return
        lastRuntimePublishElapsedMs = elapsed
        val runtime = SessionRuntime(
            sessionId = record.id,
            revision = record.revision,
            status = record.status,
            activeDurationMs = (record.config.durationMinutes * 60_000L - state.remainingMs).coerceAtLeast(0),
            remainingMs = state.remainingMs,
            reportedAtEpochMillis = System.currentTimeMillis(),
        )
        if (force) runtimePublishJob?.cancel()
        runtimePublishJob = serviceScope.launch {
            try {
                val sync = app.zhanzhuang.timer.wear.sync.WearSyncRuntime.get(applicationContext)
                if (force) sync.publishState(record)
                sync.publishRuntime(runtime)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The next low-frequency service checkpoint retries when a peer is reachable.
            }
        }
    }

    private suspend fun publishTerminalState() {
        val terminal = controller.state.value.record?.takeIf { it.status in TERMINAL_STATUSES } ?: return
        try {
            app.zhanzhuang.timer.wear.sync.WearSyncRuntime.get(applicationContext).publishState(terminal)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // WearCompletionSyncWorker retains the completed DataItem retry path.
        }
    }

    private sealed interface ServiceRequest {
        data class Command(val command: WearSessionService.Command, val expectedSessionId: String?, val requestId: String?, val startId: Int, val generation: Long) : ServiceRequest
        data class Tick(val startId: Int, val generation: Long) : ServiceRequest
    }

    private fun promoteToForeground() {
        val hasHeartRatePermission = WearForegroundPolicy.hasHeartRatePermission(this)
        ServiceCompat.startForeground(
            this,
            OngoingSessionNotification.NOTIFICATION_ID,
            notification.build(controller.state.value, notification.openActivityIntent()),
            WearForegroundPolicy.serviceTypeMask(hasHeartRatePermission),
        )
    }

    sealed interface Command {
        data class Start(val config: SessionConfig, val sessionId: String) : Command
        data object Finish : Command
        data object Cancel : Command
        data object Pause : Command
        data object Resume : Command
        data object Status : Command
    }

    companion object {
        const val ACTION_START = "app.zhanzhuang.timer.wear.session.START"
        const val ACTION_FINISH = "app.zhanzhuang.timer.wear.session.FINISH"
        const val ACTION_CANCEL = "app.zhanzhuang.timer.wear.session.CANCEL"
        const val ACTION_PAUSE = "app.zhanzhuang.timer.wear.session.PAUSE"
        const val ACTION_RESUME = "app.zhanzhuang.timer.wear.session.RESUME"
        const val ACTION_STATUS = "app.zhanzhuang.timer.wear.session.STATUS"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val EXTRA_INTERVAL_MINUTES = "interval_minutes"
        private const val TICK_MS = 1_000L
        private const val RUNTIME_PUBLISH_INTERVAL_MS = 5_000L
        private val TERMINAL_STATUSES = setOf(
            app.zhanzhuang.timer.model.SessionStatus.COMPLETED,
            app.zhanzhuang.timer.model.SessionStatus.CANCELLED,
            app.zhanzhuang.timer.model.SessionStatus.INTERRUPTED,
        )
        private val ACTIVE_STATUSES = setOf(
            app.zhanzhuang.timer.model.SessionStatus.STARTING,
            app.zhanzhuang.timer.model.SessionStatus.RUNNING,
            app.zhanzhuang.timer.model.SessionStatus.PAUSED,
            app.zhanzhuang.timer.model.SessionStatus.COMPLETING,
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
            ACTION_PAUSE -> Command.Pause
            ACTION_RESUME -> Command.Resume
            ACTION_STATUS -> Command.Status
            else -> null
        }

        private val fallbackBootId: String by lazy {
            "unavailable:${android.os.Process.myPid()}:${android.os.SystemClock.elapsedRealtime()}"
        }
        fun currentBootId(context: Context, readBootId: () -> String = { File("/proc/sys/kernel/random/boot_id").readText().trim() }): String = runCatching {
            readBootId().takeIf { it.isNotEmpty() }
                ?: error("empty kernel boot id")
        }.getOrElse {
            fallbackBootId
        }
    }
}

/** Pure command actor used by the service; every command recovers durable state before acting. */
class WearSessionCommandProcessor(private val controller: WearSessionController) {
    private val mutex = Mutex()

    suspend fun process(command: WearSessionService.Command, bootId: String, expectedSessionId: String? = null): ServiceCommandResult = mutex.withLock {
        when (controller.recover(bootId)) {
            WearSessionRecovery.NO_SESSION -> when (command) {
                is WearSessionService.Command.Start -> { controller.start(command.config, command.sessionId); ServiceCommandResult.ACTIVE }
                else -> ServiceCommandResult.STOP
            }
            WearSessionRecovery.TERMINAL -> when (command) {
                is WearSessionService.Command.Start -> if (controller.state.value.record?.id == command.sessionId) {
                    ServiceCommandResult.REJECTED
                } else {
                    controller.start(command.config, command.sessionId)
                    ServiceCommandResult.ACTIVE
                }
                else -> ServiceCommandResult.STOP
            }
            WearSessionRecovery.ACTIVE -> if (expectedSessionId != null && controller.state.value.record?.id != expectedSessionId) {
                ServiceCommandResult.REJECTED
            } else when (command) {
                // Reject the requested new session, but retain the recovered active one.
                is WearSessionService.Command.Start -> ServiceCommandResult.ACTIVE
                WearSessionService.Command.Finish -> { controller.finish(); ServiceCommandResult.STOP }
                WearSessionService.Command.Cancel -> { controller.finish(cancelled = true); ServiceCommandResult.STOP }
                WearSessionService.Command.Pause -> { controller.pause(); ServiceCommandResult.ACTIVE }
                WearSessionService.Command.Resume -> { controller.resume(); ServiceCommandResult.ACTIVE }
                WearSessionService.Command.Status -> ServiceCommandResult.ACTIVE
            }
        }
    }
}

enum class ServiceCommandResult { ACTIVE, STOP, REJECTED }
