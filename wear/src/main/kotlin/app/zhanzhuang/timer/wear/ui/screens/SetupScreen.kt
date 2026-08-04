package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
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
    val scroll = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val rotaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rotaryFocus.requestFocus() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .focusRequester(rotaryFocus)
            .focusable()
            .rotaryScrollable(
                behavior = RotaryScrollableDefaults.snapBehavior(scrollableState = scroll),
                focusRequester = rotaryFocus,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ScalingLazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RoundScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = scroll),
            rotaryScrollableBehavior = null,
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.duration), color = colors.primaryContainer)
                    StepperRow(
                        value = stringResource(R.string.minutes_value, config.durationMinutes),
                        description = stringResource(R.string.duration_description, config.durationMinutes),
                        onDecrease = { onConfigChange(WearSetupState(config).withDurationDelta(-1).config) },
                        onIncrease = { onConfigChange(WearSetupState(config).withDurationDelta(1).config) },
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.interval), color = colors.primaryContainer)
                    StepperRow(
                        value = stringResource(R.string.every_minutes, config.intervalMinutes),
                        description = stringResource(R.string.interval_description, config.intervalMinutes),
                        onDecrease = { onConfigChange(WearSetupState(config).withIntervalDelta(-1).config) },
                        onIncrease = { onConfigChange(WearSetupState(config).withIntervalDelta(1).config) },
                    )
                }
            }
            item {
                GoldAction(stringResource(R.string.start_session), onClick = onStart, modifier = Modifier.padding(horizontal = RoundActionHorizontalInset))
            }
            item { Text(stringResource(R.string.shortcuts), color = colors.primaryContainer) }
            listOf(15, 30, 45, 60, 90, 120, 180).forEach { minutes ->
                item {
                    SoilAction(stringResource(R.string.minutes_value, minutes), stringResource(R.string.set_duration, minutes), onClick = {
                        onConfigChange(WearSetupState(config).withDuration(minutes).config)
                    }, modifier = Modifier.padding(horizontal = RoundActionHorizontalInset, vertical = 2.dp))
                }
            }
        }
        ScrollIndicator(state = scroll, modifier = Modifier.align(Alignment.CenterEnd))
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
