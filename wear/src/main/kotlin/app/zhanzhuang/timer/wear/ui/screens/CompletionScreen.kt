package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import app.zhanzhuang.timer.wear.ui.formatWearDuration
import app.zhanzhuang.timer.wear.R

fun completionLabel(status: SessionStatus?): String = when (status) {
    SessionStatus.COMPLETED -> "Completed"
    SessionStatus.CANCELLED -> "Cancelled"
    SessionStatus.INTERRUPTED -> "Interrupted"
    else -> "Session ended"
}

@Composable
fun CompletionScreen(state: WearSessionUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val statusLabel = when (state.record?.status) {
        SessionStatus.COMPLETED -> stringResource(R.string.terminal_completed)
        SessionStatus.CANCELLED -> stringResource(R.string.terminal_cancelled)
        SessionStatus.INTERRUPTED -> stringResource(R.string.terminal_interrupted)
        else -> stringResource(R.string.end_session)
    }
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        ScreenColumn {
            Text(statusLabel, color = MaterialTheme.colorScheme.primaryContainer, fontWeight = FontWeight.Bold)
            Text(formatWearDuration(state.record?.activeDurationMs ?: 0L), color = MaterialTheme.colorScheme.onBackground)
            state.currentHeartRateBpm?.let { Text(stringResource(R.string.last_heart_rate, it.toInt()), color = MaterialTheme.colorScheme.primaryContainer) }
            GoldAction(stringResource(R.string.done), onClick = onDone)
        }
    }
}
