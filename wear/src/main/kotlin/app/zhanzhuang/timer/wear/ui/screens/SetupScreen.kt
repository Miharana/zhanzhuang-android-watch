package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Text
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.wear.ui.WearSetupState
import app.zhanzhuang.timer.wear.ui.theme.DeepSoil
import app.zhanzhuang.timer.wear.ui.theme.GoldHighlight
import app.zhanzhuang.timer.wear.ui.theme.WarmPaper

@Composable
fun SetupScreen(
    config: SessionConfig,
    onConfigChange: (SessionConfig) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val rotaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rotaryFocus.requestFocus() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSoil)
            .focusRequester(rotaryFocus)
            .focusable()
            .rotaryScrollable(
                behavior = RotaryScrollableDefaults.behavior(scroll),
                focusRequester = rotaryFocus,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ScreenColumn(scrollState = scroll) {
            Text("站桩", color = GoldHighlight)
            Text("Standing meditation", color = WarmPaper)
            Text("Duration", color = GoldHighlight)
            StepperRow(
                value = "${config.durationMinutes} min",
                description = "Duration ${config.durationMinutes} minutes",
                onDecrease = { onConfigChange(WearSetupState(config).withDurationDelta(-1).config) },
                onIncrease = { onConfigChange(WearSetupState(config).withDurationDelta(1).config) },
            )
            Text("Interval", color = GoldHighlight)
            StepperRow(
                value = "Every ${config.intervalMinutes} min",
                description = "Interval every ${config.intervalMinutes} minutes",
                onDecrease = { onConfigChange(WearSetupState(config).withIntervalDelta(-1).config) },
                onIncrease = { onConfigChange(WearSetupState(config).withIntervalDelta(1).config) },
            )
            Text("Shortcuts", color = GoldHighlight)
            listOf(15, 30, 45, 60, 90, 120, 180).forEach { minutes ->
                SoilAction("$minutes min", "Set duration to $minutes minutes", onClick = {
                    onConfigChange(WearSetupState(config).withDuration(minutes).config)
                })
            }
            GoldAction("Start", onClick = onStart)
        }
    }
}

@Composable
private fun StepperRow(value: String, description: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SoilAction("−", "Decrease $description", onDecrease, Modifier.weight(1f))
        Text(value, modifier = Modifier.weight(2f).semantics { stateDescription = description }, color = WarmPaper)
        SoilAction("+", "Increase $description", onIncrease, Modifier.weight(1f))
    }
}
