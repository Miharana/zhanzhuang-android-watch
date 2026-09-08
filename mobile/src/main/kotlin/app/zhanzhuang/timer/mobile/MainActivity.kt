package app.zhanzhuang.timer.mobile

import android.animation.ValueAnimator
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import app.zhanzhuang.timer.mobile.data.MobileDatabase
import app.zhanzhuang.timer.mobile.data.MobileSessionRepository
import app.zhanzhuang.timer.mobile.health.AndroidHealthConnectGateway
import app.zhanzhuang.timer.mobile.health.HealthPermissionState
import app.zhanzhuang.timer.mobile.health.HealthSyncWorker
import app.zhanzhuang.timer.mobile.health.SharedPreferencesHealthExportConsent
import app.zhanzhuang.timer.mobile.session.MobileSessionService
import app.zhanzhuang.timer.mobile.session.MobileSessionUiBridge
import app.zhanzhuang.timer.mobile.sync.MobileSyncCoordinator
import app.zhanzhuang.timer.mobile.sync.MobileSyncRuntime
import app.zhanzhuang.timer.mobile.sync.MobileWearRuntimeBridge
import app.zhanzhuang.timer.mobile.ui.HealthPermissionPort
import app.zhanzhuang.timer.mobile.ui.MainViewModel
import app.zhanzhuang.timer.mobile.ui.MobileDestination
import app.zhanzhuang.timer.mobile.ui.MobileTrainingActions
import app.zhanzhuang.timer.mobile.ui.SharedPreferencesTrainingDefaults
import app.zhanzhuang.timer.mobile.ui.WatchConnectionPort
import app.zhanzhuang.timer.mobile.ui.WatchConnectionState
import app.zhanzhuang.timer.mobile.ui.WearRuntimePort
import app.zhanzhuang.timer.mobile.ui.components.GoldSparkle
import app.zhanzhuang.timer.mobile.ui.screens.HistoryScreen
import app.zhanzhuang.timer.mobile.ui.screens.SessionDetailScreen
import app.zhanzhuang.timer.mobile.ui.screens.SettingsScreen
import app.zhanzhuang.timer.mobile.ui.screens.TrainingScreen
import app.zhanzhuang.timer.mobile.ui.theme.ZhanZhuangTheme
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.CAPABILITY_ZHAN_ZHUANG_WEAR
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.shouldRequestNotificationPermission
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.start()
    }
    private val requestHealthPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.onHealthPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        viewModel = ViewModelProvider(this, MainViewModelFactory(applicationContext))[MainViewModel::class.java]
        setContent {
            ZhanZhuangTheme {
                MobileApp(viewModel)
            }
        }
    }

    @Composable
    private fun MobileApp(model: MainViewModel) {
        val state by model.state.collectAsState()
        val context = LocalContext.current
        var sparkle by remember { mutableStateOf(false) }
        val motionAllowed = !context.getSystemService(PowerManager::class.java).isPowerSaveMode && ValueAnimator.areAnimatorsEnabled()
        LaunchedEffect(state.training.session?.id, state.training.session?.status) {
            if (state.training.session?.status in setOf(app.zhanzhuang.timer.model.SessionStatus.RUNNING, app.zhanzhuang.timer.model.SessionStatus.COMPLETED)) sparkle = true
        }
        LaunchedEffect(Unit) { model.permissionRequests.collect { requestHealthPermissions.launch(it) } }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            when (state.destination) {
                MobileDestination.TRAINING -> TrainingScreen(
                    state = state.training,
                    onDurationChange = model::selectDuration,
                    onIntervalChange = model::selectInterval,
                    onStart = ::startWithNotificationPermission,
                    onPause = model::pause,
                    onResume = model::resume,
                    onFinish = { model.finish(cancelled = false) },
                    onCancel = { model.finish(cancelled = true) },
                    onHistory = { model.open(MobileDestination.HISTORY) },
                    onSettings = { model.open(MobileDestination.SETTINGS) },
                )
                MobileDestination.HISTORY -> HistoryScreen(
                    records = state.history,
                    stats = state.historyStats,
                    onOpen = model::showDetail,
                    onBack = { model.open(MobileDestination.TRAINING) },
                )
                MobileDestination.DETAIL -> state.selectedRecord?.let { SessionDetailScreen(it, model::backToHistory) }
                MobileDestination.SETTINGS -> SettingsScreen(
                    config = state.training.config,
                    health = state.health,
                    onDurationChange = model::selectDuration,
                    onIntervalChange = model::selectInterval,
                    onAcceptHealthDisclosure = model::requestHealthPermissions,
                    onRetryHealth = model::retryHealthSync,
                    onBack = { model.open(MobileDestination.TRAINING) },
                )
            }
            GoldSparkle(enabled = sparkle && motionAllowed, onFinished = { sparkle = false })
        }
    }

    private fun startWithNotificationPermission() {
        val granted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (shouldRequestNotificationPermission(Build.VERSION.SDK_INT, granted)) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }
}

private class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = MobileSessionRepository(
            Room.databaseBuilder(context, MobileDatabase::class.java, DATABASE_NAME)
                .addMigrations(MobileDatabase.MIGRATION_1_2)
                .build(),
        )
        val healthConsent = SharedPreferencesHealthExportConsent(context)
        val gateway = AndroidHealthConnectGateway(context, healthConsent)
        val coordinator = MobileSyncRuntime.get(context)
        return MainViewModel(
            repository = repository,
            serviceState = MobileSessionUiBridge.state,
            trainingActions = AndroidTrainingActions(context, coordinator),
            defaults = SharedPreferencesTrainingDefaults(context),
            watchConnection = AndroidWatchConnectionPort(context, coordinator),
            wearRuntime = object : WearRuntimePort { override val states = MobileWearRuntimeBridge.runtime },
            healthGateway = gateway,
            healthPermission = AndroidHealthPermissionPort(context),
            healthExportConsent = healthConsent,
            reconcileHealth = { HealthSyncWorker.reconcile(context) },
        ) as T
    }

    private companion object { const val DATABASE_NAME = "zhan_zhuang.db" }
}

private class AndroidTrainingActions(
    private val context: Context,
    private val coordinator: MobileSyncCoordinator,
) : MobileTrainingActions {
    override suspend fun start(config: SessionConfig, watchReachable: Boolean): SessionRecord =
        if (watchReachable) coordinator.startFromMobile(config) else coordinator.startOnMobile(config)

    override suspend fun pause(record: SessionRecord) {
        if (record.owner == SessionOwner.WEAR) {
            check(coordinator.requestPause(record.id)) { "The watch could not be reached" }
        }
        else MobileSessionService.command(context, MobileSessionService.ACTION_PAUSE)
    }

    override suspend fun resume(record: SessionRecord) {
        if (record.owner == SessionOwner.WEAR) {
            check(coordinator.requestResume(record.id)) { "The watch could not be reached" }
        }
        else MobileSessionService.command(context, MobileSessionService.ACTION_RESUME)
    }

    override suspend fun finish(record: SessionRecord, cancelled: Boolean) {
        if (record.owner == SessionOwner.WEAR) {
            check(coordinator.requestFinish(record.id, cancelled)) { "The watch could not be reached" }
        }
        else MobileSessionService.command(context, MobileSessionService.ACTION_FINISH, cancelled)
    }
}

private class AndroidHealthPermissionPort(private val context: Context) : HealthPermissionPort {
    override suspend fun state(required: Set<String>): HealthPermissionState {
        return try {
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
                HealthPermissionState.Missing(required)
            } else {
                val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
                val missing = required - granted
                if (missing.isEmpty()) HealthPermissionState.Granted else HealthPermissionState.Missing(missing)
            }
        } catch (_: Exception) {
            HealthPermissionState.Missing(required)
        }
    }
}

private class AndroidWatchConnectionPort(
    context: Context,
    private val coordinator: MobileSyncCoordinator,
) : WatchConnectionPort {
    private val applicationContext = context.applicationContext
    override val states = callbackFlow {
        val client = Wearable.getCapabilityClient(applicationContext)
        var wasConnected = false
        fun publish(capability: CapabilityInfo?) {
            val connection = capability?.toConnection() ?: WatchConnectionState(connected = false)
            val reconnected = connection.connected && !wasConnected
            wasConnected = connection.connected
            trySend(connection)
            if (reconnected) launch { runCatching { coordinator.queryCurrentWearState() } }
        }
        val listener = CapabilityClient.OnCapabilityChangedListener(::publish)
        client.addListener(listener, CAPABILITY_ZHAN_ZHUANG_WEAR)
        launch {
            runCatching { client.getCapability(CAPABILITY_ZHAN_ZHUANG_WEAR, CapabilityClient.FILTER_REACHABLE).await() }
                .getOrNull()
                .let(::publish)
        }
        awaitClose { client.removeListener(listener, CAPABILITY_ZHAN_ZHUANG_WEAR) }
    }.distinctUntilChanged()

    private fun CapabilityInfo.toConnection(): WatchConnectionState = WatchConnectionState(
        connected = nodes.isNotEmpty(),
        name = nodes.firstOrNull()?.displayName,
    )

}
