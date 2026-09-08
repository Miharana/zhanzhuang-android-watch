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
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import app.zhanzhuang.timer.wear.ui.formatWearDuration
import app.zhanzhuang.timer.wear.R

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
    val remainingDescription = stringResource(R.string.seconds_remaining, state.remainingMs / 1_000L)
    Box(modifier = modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        ScreenColumn {
            Text(if (paused) stringResource(R.string.paused) else stringResource(R.string.standing), color = colors.primaryContainer)
            Text(
                text = if (ambient) stringResource(R.string.minutes_value, (state.remainingMs + 59_999L) / 60_000L) else formatWearDuration(state.remainingMs),
                color = colors.onBackground,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 30.sp, fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { stateDescription = remainingDescription },
            )
            state.currentHeartRateBpm?.let { bpm -> Text(stringResource(R.string.last_heart_rate, bpm.toInt()), color = colors.primaryContainer) }
            state.heartRateUnavailableReason?.let { Text(stringResource(R.string.duration_only), color = colors.primaryContainer) }
            if (paused) GoldAction(stringResource(R.string.resume_session), onClick = onResume) else SoilAction(stringResource(R.string.pause_session), onClick = onPause)
            SoilAction(stringResource(R.string.end_session), stringResource(R.string.end_session), onClick = { confirmingFinish = true })
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
                Text(stringResource(R.string.end_session_question), color = MaterialTheme.colorScheme.onBackground)
                GoldAction(stringResource(R.string.end_session), stringResource(R.string.confirm_end_session), onClick = onConfirm)
                SoilAction(stringResource(R.string.cancel_session), stringResource(R.string.cancel_session), onClick = onCancel)
                SoilAction(stringResource(R.string.keep_standing), onClick = onDismiss)
            }
        }
    }
}
