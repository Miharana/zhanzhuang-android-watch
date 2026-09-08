package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.wear.R
import app.zhanzhuang.timer.wear.ui.WearSetupState

@Composable
fun SetupScreen(
    config: SessionConfig,
    onConfigChange: (SessionConfig) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val rotaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rotaryFocus.requestFocus() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .focusRequester(rotaryFocus)
            .focusable()
            .rotaryScrollable(
                behavior = RotaryScrollableDefaults.behavior(scroll),
                focusRequester = rotaryFocus,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ScreenColumn(scrollState = scroll, verticalSpacing = 0.dp) {
            Text(stringResource(R.string.duration), color = colors.primaryContainer)
            StepperRow(
                value = stringResource(R.string.minutes_value, config.durationMinutes),
                description = stringResource(R.string.duration_description, config.durationMinutes),
                onDecrease = { onConfigChange(WearSetupState(config).withDurationDelta(-1).config) },
                onIncrease = { onConfigChange(WearSetupState(config).withDurationDelta(1).config) },
            )
            Text(stringResource(R.string.interval), color = colors.primaryContainer)
            StepperRow(
                value = stringResource(R.string.every_minutes, config.intervalMinutes),
                description = stringResource(R.string.interval_description, config.intervalMinutes),
                onDecrease = { onConfigChange(WearSetupState(config).withIntervalDelta(-1).config) },
                onIncrease = { onConfigChange(WearSetupState(config).withIntervalDelta(1).config) },
            )
            GoldAction(stringResource(R.string.start_session), onClick = onStart, modifier = Modifier.padding(horizontal = RoundActionHorizontalInset))
            Spacer(Modifier.height(RoundActionBottomSafeSpace))
            Text(stringResource(R.string.shortcuts), color = colors.primaryContainer)
            listOf(15, 30, 45, 60, 90, 120, 180).forEach { minutes ->
                SoilAction(stringResource(R.string.minutes_value, minutes), stringResource(R.string.set_duration, minutes), onClick = {
                    onConfigChange(WearSetupState(config).withDuration(minutes).config)
                })
            }
        }
    }
}

@Composable
private fun StepperRow(value: String, description: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SoilAction("−", stringResource(R.string.decrease, description), onDecrease, Modifier.weight(1f))
        Text(value, modifier = Modifier.weight(2f).semantics { stateDescription = description }, color = MaterialTheme.colorScheme.onSurface)
        SoilAction("+", stringResource(R.string.increase, description), onIncrease, Modifier.weight(1f))
    }
}
