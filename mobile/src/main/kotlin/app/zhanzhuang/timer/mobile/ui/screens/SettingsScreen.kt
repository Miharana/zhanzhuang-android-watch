package app.zhanzhuang.timer.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.zhanzhuang.timer.mobile.health.HealthAvailability
import app.zhanzhuang.timer.mobile.health.HealthPermissionState
import app.zhanzhuang.timer.mobile.ui.HealthUiState
import app.zhanzhuang.timer.model.SessionConfig

@Composable
fun SettingsScreen(
    config: SessionConfig,
    health: HealthUiState,
    onDurationChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onRequestHealthPermission: () -> Unit,
    onRetryHealth: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var legalNotice by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Defaults", style = MaterialTheme.typography.titleLarge)
        Text("Duration: ${config.durationMinutes} min")
        OutlinedButton(onClick = { onDurationChange((config.durationMinutes + 5).takeIf { it <= 180 } ?: 15) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Change duration default") }
        Text("Interval: every ${config.intervalMinutes} min")
        OutlinedButton(onClick = { onIntervalChange(listOf(5, 10, 15, 20, 30).let { values -> values[(values.indexOf(config.intervalMinutes) + 1) % values.size] }) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Change interval default") }
        Text("Health Connect", style = MaterialTheme.typography.titleLarge)
        Text(healthLabel(health))
        if (health.permission is HealthPermissionState.Missing && health.availability != HealthAvailability.UNAVAILABLE) Button(onClick = onRequestHealthPermission, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Allow Health Connect writing") }
        OutlinedButton(onClick = onRetryHealth, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Retry completed session sync") }
        Text("Privacy", style = MaterialTheme.typography.titleLarge)
        Text("Sessions stay on this device. There are no accounts, ads, analytics, or cloud backup.")
        OutlinedButton(onClick = { legalNotice = "Privacy policy\n\nThis app stores practice records locally. Optional Health Connect writes happen only after you allow them. No account, ads, analytics, or cloud service is used." }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Privacy policy") }
        OutlinedButton(onClick = { legalNotice = "Open-source licenses\n\nLicense notices will be included with the release package." }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open-source licenses") }
    }
    legalNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { legalNotice = null },
            title = { Text(notice.substringBefore("\n")) },
            text = { Text(notice.substringAfter("\n\n")) },
            confirmButton = { Button(onClick = { legalNotice = null }) { Text("Close") } },
        )
    }
}

private fun healthLabel(health: HealthUiState) = when (health.availability) {
    HealthAvailability.AVAILABLE -> when (health.permission) { HealthPermissionState.Granted -> "Available and allowed"; is HealthPermissionState.Missing -> "Available. Permission is optional." }
    HealthAvailability.MINDFULNESS_UNSUPPORTED -> "Available with exercise-session fallback"
    HealthAvailability.UNAVAILABLE, null -> "Unavailable on this phone"
}
