package app.zhanzhuang.timer.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import app.zhanzhuang.timer.R
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
    onAcceptHealthDisclosure: () -> Unit,
    onRetryHealth: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var legalNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var healthDisclosureVisible by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.defaults), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.duration_default, config.durationMinutes))
        OutlinedButton(onClick = { onDurationChange((config.durationMinutes + 5).takeIf { it <= 180 } ?: 15) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.change_duration_default)) }
        Text(stringResource(R.string.interval_default, config.intervalMinutes))
        OutlinedButton(onClick = { onIntervalChange(listOf(5, 10, 15, 20, 30).let { values -> values[(values.indexOf(config.intervalMinutes) + 1) % values.size] }) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.change_interval_default)) }
        Text(stringResource(R.string.health_connect), style = MaterialTheme.typography.titleLarge)
        Text(healthLabel(health))
        if (health.availability != HealthAvailability.UNAVAILABLE &&
            (!health.exportConsentAccepted || health.permission is HealthPermissionState.Missing)
        ) {
            Button(onClick = { healthDisclosureVisible = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (health.exportConsentAccepted) R.string.allow_health_writing else R.string.setup_health_export))
            }
        }
        if (health.exportConsentAccepted) Text(stringResource(R.string.health_export_consent_active))
        OutlinedButton(onClick = onRetryHealth, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.retry_health_sync)) }
        Text(stringResource(R.string.privacy), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.privacy_summary))
        OutlinedButton(onClick = { legalNotice = "privacy" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_policy)) }
        Spacer(Modifier.height(64.dp))
        OutlinedButton(onClick = { legalNotice = "licenses" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.open_source_licenses)) }
    }
    legalNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { legalNotice = null },
            title = { Text(stringResource(if (notice == "privacy") R.string.privacy_policy else R.string.open_source_licenses)) },
            text = { Text(stringResource(if (notice == "privacy") R.string.privacy_policy_body else R.string.licenses_body)) },
            confirmButton = { Button(onClick = { legalNotice = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (healthDisclosureVisible) {
        AlertDialog(
            onDismissRequest = { healthDisclosureVisible = false },
            title = { Text(stringResource(R.string.health_export_disclosure_title)) },
            text = { Text(stringResource(R.string.health_export_disclosure_body)) },
            dismissButton = {
                OutlinedButton(onClick = { healthDisclosureVisible = false }) {
                    Text(stringResource(R.string.not_now))
                }
            },
            confirmButton = {
                Button(onClick = {
                    healthDisclosureVisible = false
                    onAcceptHealthDisclosure()
                }) {
                    Text(stringResource(R.string.accept_and_continue))
                }
            },
        )
    }
}

@Composable private fun healthLabel(health: HealthUiState) = when (health.availability) {
    HealthAvailability.AVAILABLE -> when (health.permission) { HealthPermissionState.Granted -> stringResource(R.string.health_available_allowed); is HealthPermissionState.Missing -> stringResource(R.string.health_available_optional) }
    HealthAvailability.MINDFULNESS_UNSUPPORTED -> stringResource(R.string.health_fallback)
    HealthAvailability.UNAVAILABLE, null -> stringResource(R.string.health_unavailable)
}
