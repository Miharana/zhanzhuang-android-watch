package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import app.zhanzhuang.timer.wear.ui.formatWearDuration

@Composable
fun ActiveSessionScreen(
    state: WearSessionUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit = onFinish,
    ambient: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var confirmingFinish by remember { mutableStateOf(false) }
    val paused = state.record?.status == SessionStatus.PAUSED
    Box(modifier = modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        ScreenColumn {
            Text(if (paused) "Paused" else "Standing", color = colors.primaryContainer)
            Text(
                text = if (ambient) "${(state.remainingMs + 59_999L) / 60_000L} min" else formatWearDuration(state.remainingMs),
                color = colors.onBackground,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 30.sp, fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { stateDescription = "${state.remainingMs / 1_000L} seconds remaining" },
            )
            state.currentHeartRateBpm?.let { bpm -> Text("${bpm.toInt()} bpm", color = colors.primaryContainer) }
            state.heartRateUnavailableReason?.let { Text("Duration only", color = colors.primaryContainer) }
            if (paused) GoldAction("Resume", onClick = onResume) else SoilAction("Pause", onClick = onPause)
            SoilAction("End", "End session", onClick = { confirmingFinish = true })
        }
    }
    if (confirmingFinish) EndSessionConfirmation(
        onDismiss = { confirmingFinish = false },
        onConfirm = { confirmingFinish = false; onFinish() },
        onCancel = { confirmingFinish = false; onCancel() },
    )
}

@Composable
private fun EndSessionConfirmation(onDismiss: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            ScreenColumn {
                Text("End this session?", color = MaterialTheme.colorScheme.onBackground)
                GoldAction("End session", "Confirm end session", onClick = onConfirm)
                SoilAction("Cancel session", "Cancel session", onClick = onCancel)
                SoilAction("Keep standing", onClick = onDismiss)
            }
        }
    }
}
