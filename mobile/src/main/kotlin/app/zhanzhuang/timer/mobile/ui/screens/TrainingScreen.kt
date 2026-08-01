package app.zhanzhuang.timer.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("站桩", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Standing meditation", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton("History", onHistory)
                TextButton("Settings", onSettings)
            }
        }
        HorizontalDivider()
        if (active) ActiveTraining(state, onPause, onResume, onFinish, onCancel)
        else if (state.session?.status in TERMINAL_STATUSES) TerminalTraining(state)
        else SetupTraining(state, onDurationChange, onIntervalChange, onStart)
    }
}

@Composable
private fun TerminalTraining(state: TrainingUiState) {
    val status = state.session?.status ?: return
    Text(
        when (status) {
            SessionStatus.COMPLETED -> "Completed"
            SessionStatus.CANCELLED -> "Cancelled"
            SessionStatus.INTERRUPTED -> "Interrupted"
            else -> status.name
        },
        style = MaterialTheme.typography.headlineMedium,
    )
    Text("${state.activeElapsedMs / 60_000L} minutes active")
    Text("Set up another session above from History or Settings when ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SetupTraining(
    state: TrainingUiState,
    onDurationChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Text("Duration", style = MaterialTheme.typography.titleLarge)
    Stepper(
        value = "${state.config.durationMinutes} min",
        description = "Duration ${state.config.durationMinutes} minutes",
        onMinus = { onDurationChange((state.config.durationMinutes - 5).coerceAtLeast(15)) },
        onPlus = { onDurationChange((state.config.durationMinutes + 5).coerceAtMost(180)) },
    )
    ChoiceFlow(durationShortcuts, state.config.durationMinutes, { "$it min" }, onDurationChange)
    Text("Haptic interval", style = MaterialTheme.typography.titleLarge)
    ChoiceFlow(intervals, state.config.intervalMinutes, { "Every $it min" }, onIntervalChange)
    Text("Your watch vibrates at the interval. No sound is used.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    WatchStatus(state)
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) { Text("Start standing", fontWeight = FontWeight.Bold) }
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
    Text(if (paused) "Paused" else if (state.session?.owner == SessionOwner.WEAR) "Standing on watch" else "Standing", style = MaterialTheme.typography.titleLarge)
    Text(
        formatDuration(state.remainingMs),
        style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.semantics { stateDescription = "${state.remainingMs / 1_000L} seconds remaining" },
    )
    if (state.session?.owner == SessionOwner.WEAR) {
        Text("Your watch owns this timer; the phone will not run a second timer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    WatchStatus(state)
    if (paused) Button(onClick = onResume, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Resume") }
    else OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Pause") }
    OutlinedButton(onClick = { confirmingEnd = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("End session") }
    if (confirmingEnd) AlertDialog(
        onDismissRequest = { confirmingEnd = false },
        title = { Text("End this session?") },
        text = { Text("End keeps the active time. Cancel marks the session as cancelled in local history.") },
        confirmButton = { Button(onClick = { confirmingEnd = false; onFinish() }) { Text("End session") } },
        dismissButton = { OutlinedButton(onClick = { confirmingEnd = false; onCancel() }) { Text("Cancel session") } },
    )
}

@Composable
private fun WatchStatus(state: TrainingUiState) {
    if (state.watchConnected) {
        Text("Watch connected${state.watchName?.let { ": $it" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("Watch disconnected", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("This session won't record heart rate", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Stepper(value: String, description: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("−") }
        Text(value, modifier = Modifier.weight(2f).wrapContentWidth(Alignment.CenterHorizontally).semantics { stateDescription = description }, style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onPlus, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("+") }
    }
}

@Composable
private fun <T> ChoiceFlow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
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
