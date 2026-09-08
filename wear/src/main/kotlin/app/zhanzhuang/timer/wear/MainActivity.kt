package app.zhanzhuang.timer.wear

import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.AppScaffold
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.shouldRequestNotificationPermission
import app.zhanzhuang.timer.wear.session.WearSessionService
import app.zhanzhuang.timer.wear.session.WearSessionUiBridge
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import app.zhanzhuang.timer.wear.session.WearSessionRecoveryPolicy
import app.zhanzhuang.timer.wear.session.WearSessionRecoveryPolicy.PREFERENCES_NAME
import app.zhanzhuang.timer.wear.ui.components.GoldSparkle
import app.zhanzhuang.timer.wear.ui.screens.ActiveSessionScreen
import app.zhanzhuang.timer.wear.ui.screens.CompletionScreen
import app.zhanzhuang.timer.wear.ui.screens.PermissionScreen
import app.zhanzhuang.timer.wear.ui.screens.SetupScreen
import app.zhanzhuang.timer.wear.ui.theme.ZhanZhuangWearTheme
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private var permissionPrompt by mutableStateOf(false)
    private var pendingConfig by mutableStateOf(SessionConfig())
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        continueStartAfterNotificationPermission()
    }
    private val requestHeartRate = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionPrompt = false
        startSession(pendingConfig)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            ZhanZhuangWearTheme {
                WearApp(
                    permissionPrompt = permissionPrompt,
                    onStart = { config ->
                        pendingConfig = config
                        val granted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        if (shouldRequestNotificationPermission(Build.VERSION.SDK_INT, granted)) {
                            requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            continueStartAfterNotificationPermission()
                        }
                    },
                    onRequestPermission = { requestHeartRate.launch(heartRatePermissions()) },
                    onContinueWithoutHeartRate = { permissionPrompt = false; startSession(pendingConfig) },
                    onPause = { sendService(WearSessionService.ACTION_PAUSE) },
                    onResume = { sendService(WearSessionService.ACTION_RESUME) },
                    onFinish = { sendService(WearSessionService.ACTION_FINISH) },
                    onCancel = { sendService(WearSessionService.ACTION_CANCEL) },
                )
            }
        }
        requestStatusIfRecoverable()
    }

    private fun continueStartAfterNotificationPermission() {
        if (needsHeartRatePermission()) permissionPrompt = true else startSession(pendingConfig)
    }

    private fun startSession(config: SessionConfig) {
        ContextCompat.startForegroundService(this, WearSessionService.startIntent(this, config, UUID.randomUUID().toString()))
    }

    private fun sendService(action: String) {
        ContextCompat.startForegroundService(this, Intent(this, WearSessionService::class.java).setAction(action))
    }

    private fun requestStatusIfRecoverable() {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        if (!WearSessionRecoveryPolicy.hasSnapshot(preferences)) return

        val intent = Intent(this, WearSessionService::class.java).setAction(WearSessionService.ACTION_STATUS)
        if (WearSessionRecoveryPolicy.statusRequiresForeground(preferences)) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            // A terminal snapshot only needs to republish the completion state.
            // Do not promote a health service when no timer is active.
            startService(intent)
        }
    }

    private fun needsHeartRatePermission(): Boolean = heartRatePermissions().any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun heartRatePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 36) {
        arrayOf("android.permission.health.READ_HEART_RATE")
    } else {
        arrayOf(android.Manifest.permission.BODY_SENSORS)
    }

}

@Composable
private fun WearApp(
    permissionPrompt: Boolean,
    onStart: (SessionConfig) -> Unit,
    onRequestPermission: () -> Unit,
    onContinueWithoutHeartRate: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val ambientManager = rememberAmbientModeManager()
    val ambient = ambientManager.currentAmbientMode is AmbientMode.Ambient
    var ambientTick by remember { mutableStateOf(0) }
    // Ambient presentation has its own minute-boundary cadence and never creates a seconds timer.
    LaunchedEffect(ambient) {
        while (ambient) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            ambientTick += 1
        }
    }
    val motionAllowed = !ambient && !context.getSystemService(PowerManager::class.java).isPowerSaveMode && ValueAnimator.areAnimatorsEnabled()
    // The ambient branch collects a minute-distinct presentation flow, not the per-second controller state.
    val displayFlow = remember(ambient, ambientTick) {
        if (ambient) {
            WearSessionUiBridge.state.map(::ambientPresentation).distinctUntilChanged().map(AmbientPresentation::toUiState)
        } else {
            WearSessionUiBridge.state
        }
    }
    val display by displayFlow.collectAsState(initial = EMPTY_UI_STATE)
    var config by remember { mutableStateOf(SessionConfig()) }
    var sparkle by remember { mutableStateOf(false) }
    var dismissedCompletionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(display.record?.status) {
        if (display.record?.status in setOf(SessionStatus.RUNNING, SessionStatus.COMPLETED)) sparkle = true
    }

    AppScaffold {
        when {
            permissionPrompt -> PermissionScreen(onRequestPermission, onContinueWithoutHeartRate)
            display.record?.status in setOf(SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.STARTING, SessionStatus.COMPLETING) -> {
                ActiveSessionScreen(
                    state = display,
                    onPause = onPause,
                    onResume = onResume,
                    onFinish = onFinish,
                    onCancel = onCancel,
                    ambient = ambient,
                )
            }
            display.record?.status in setOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.INTERRUPTED) &&
                display.record?.id != dismissedCompletionId ->
                CompletionScreen(display, onDone = { dismissedCompletionId = display.record?.id })
            else -> SetupScreen(config, { config = it }, { onStart(config) })
        }
        GoldSparkle(enabled = sparkle && motionAllowed, onFinished = { sparkle = false })
    }
}

private data class AmbientPresentation(
    val id: String?,
    val status: SessionStatus?,
    val config: SessionConfig?,
    val remainingMinutes: Long,
) {
    fun toUiState(): WearSessionUiState = WearSessionUiState(
        record = if (id == null || status == null || config == null) null else SessionRecord(
            id = id,
            config = config,
            status = status,
            owner = SessionOwner.WEAR,
        ),
        remainingMs = remainingMinutes * 60_000L,
        currentHeartRateBpm = null,
        nextReminderAtActiveMs = null,
    )
}

private fun ambientPresentation(state: WearSessionUiState) = AmbientPresentation(
    id = state.record?.id,
    status = state.record?.status,
    config = state.record?.config,
    remainingMinutes = (state.remainingMs + 59_999L) / 60_000L,
)

private val EMPTY_UI_STATE = WearSessionUiState(null, 0, null, null)
