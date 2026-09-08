package app.zhanzhuang.timer.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.zhanzhuang.timer.R
import app.zhanzhuang.timer.mobile.ui.components.EBrandMark
import app.zhanzhuang.timer.mobile.ui.components.EIntervalTimer
import app.zhanzhuang.timer.mobile.ui.TrainingActionError
import app.zhanzhuang.timer.mobile.ui.TrainingUiState
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionStatus

private val durationShortcuts = listOf(15, 30, 45, 60, 90, 120, 180)
private val intervals = listOf(5, 10, 15, 20, 30)

@Composable
fun TrainingScreen(
    state: TrainingUiState,
    onDurationChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onHistory: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val active = state.session?.status in ACTIVE_STATUSES
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EBrandMark(description = stringResource(R.string.app_name))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(stringResource(R.string.history), onHistory)
                    TextButton(stringResource(R.string.settings), onSettings)
                }
            }
            HorizontalDivider()
            state.actionError?.let { error ->
                Text(
                    when (error) {
                        TrainingActionError.START_FAILED -> stringResource(R.string.session_start_failed)
                        TrainingActionError.UPDATE_FAILED -> stringResource(R.string.session_action_failed)
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when {
                active -> ActiveTraining(state, onPause, onResume, onFinish, onCancel)
                state.session?.status in TERMINAL_STATUSES -> {
                    TerminalTraining(state)
                    HorizontalDivider()
                    SetupTraining(state, onDurationChange, onIntervalChange)
                }
                else -> SetupTraining(state, onDurationChange, onIntervalChange)
            }
        }
        if (!active) {
            Button(
                onClick = onStart,
                enabled = !state.actionInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text(
                    stringResource(if (state.actionInProgress) R.string.starting_session else R.string.start_standing),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TerminalTraining(state: TrainingUiState) {
    val status = state.session?.status ?: return
    Text(
        when (status) {
            SessionStatus.COMPLETED -> stringResource(R.string.terminal_completed)
            SessionStatus.CANCELLED -> stringResource(R.string.terminal_cancelled)
            SessionStatus.INTERRUPTED -> stringResource(R.string.terminal_interrupted)
            else -> status.name
        },
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(stringResource(R.string.minutes_active, state.activeElapsedMs / 60_000L))
    Text(stringResource(R.string.setup_another_session), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SetupTraining(
    state: TrainingUiState,
    onDurationChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
) {
    Text(stringResource(R.string.duration), style = MaterialTheme.typography.titleLarge)
    Stepper(
        value = stringResource(R.string.minutes_value, state.config.durationMinutes),
        description = stringResource(R.string.duration_description, state.config.durationMinutes),
        onMinus = { onDurationChange((state.config.durationMinutes - 5).coerceAtLeast(15)) },
        onPlus = { onDurationChange((state.config.durationMinutes + 5).coerceAtMost(180)) },
    )
    ChoiceFlow(durationShortcuts, state.config.durationMinutes, { stringResource(R.string.minutes_value, it) }, onDurationChange)
    Text(stringResource(R.string.haptic_interval), style = MaterialTheme.typography.titleLarge)
    ChoiceFlow(intervals, state.config.intervalMinutes, { stringResource(R.string.every_minutes, it) }, onIntervalChange)
    Text(stringResource(R.string.haptic_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
    WatchStatus(state)
}

@Composable
private fun ActiveTraining(
    state: TrainingUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirmingEnd by rememberSaveable { mutableStateOf(false) }
    val paused = state.session?.status == SessionStatus.PAUSED
    val remainingDescription = stringResource(R.string.seconds_remaining, state.remainingMs / 1_000L)
    Text(if (paused) stringResource(R.string.paused) else if (state.session?.owner == SessionOwner.WEAR) stringResource(R.string.standing_on_watch) else stringResource(R.string.standing), style = MaterialTheme.typography.titleLarge)
    EIntervalTimer(
        activeElapsedMs = state.activeElapsedMs,
        config = state.session?.config ?: state.config,
        description = remainingDescription,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    Text(
        formatDuration(state.remainingMs),
        style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.semantics { stateDescription = remainingDescription },
    )
    if (state.session?.owner == SessionOwner.WEAR) {
        Text(stringResource(R.string.watch_owns_timer), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    WatchStatus(state)
    if (paused) Button(onClick = onResume, enabled = !state.actionInProgress, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.resume_session)) }
    else OutlinedButton(onClick = onPause, enabled = !state.actionInProgress, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.pause_session)) }
    OutlinedButton(onClick = { confirmingEnd = true }, enabled = !state.actionInProgress, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.end_session)) }
    if (confirmingEnd) AlertDialog(
        onDismissRequest = { confirmingEnd = false },
        title = { Text(stringResource(R.string.end_session_question)) },
        text = { Text(stringResource(R.string.end_session_explanation)) },
        confirmButton = { Button(onClick = { confirmingEnd = false; onFinish() }) { Text(stringResource(R.string.end_session)) } },
        dismissButton = { OutlinedButton(onClick = { confirmingEnd = false; onCancel() }) { Text(stringResource(R.string.cancel_session)) } },
    )
}

@Composable
private fun WatchStatus(state: TrainingUiState) {
    if (state.watchConnected) {
        Text(state.watchName?.let { stringResource(R.string.watch_connected, it) } ?: stringResource(R.string.watch_connected_generic), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(stringResource(R.string.watch_disconnected), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.watch_no_heart_rate), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Stepper(value: String, description: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val decreaseDescription = stringResource(R.string.decrease, description)
    val increaseDescription = stringResource(R.string.increase, description)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = decreaseDescription }) { Text("−") }
        Text(value, modifier = Modifier.weight(2f).wrapContentWidth(Alignment.CenterHorizontally).semantics { stateDescription = description }, style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onPlus, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = increaseDescription }) { Text("+") }
    }
}

@Composable
private fun <T> ChoiceFlow(values: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { value ->
                    FilterChip(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        label = { Text(label(value)) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
                repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000L).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

private val ACTIVE_STATUSES = setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
private val TERMINAL_STATUSES = setOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.INTERRUPTED)
