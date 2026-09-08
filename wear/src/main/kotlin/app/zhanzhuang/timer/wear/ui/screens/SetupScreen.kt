package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
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
    val scroll = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = scroll, modifier = modifier.fillMaxSize().background(colors.background)) { contentPadding ->
        TransformingLazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = scroll),
            rotaryScrollableBehavior = RotaryScrollableDefaults.snapBehavior(scrollableState = scroll),
        ) {
            item {
                val itemScope = this
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.duration),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RoundScreenHorizontalPadding)
                            .transformedHeight(itemScope, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContentTransformation()
                                    applyContainerTransformation()
                                }
                            },
                        color = colors.primaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    StepperRow(
                        value = stringResource(R.string.minutes_value, config.durationMinutes),
                        description = stringResource(R.string.duration_description, config.durationMinutes),
                        onDecrease = { onConfigChange(WearSetupState(config).withDurationDelta(-1).config) },
                        onIncrease = { onConfigChange(WearSetupState(config).withDurationDelta(1).config) },
                        modifier = Modifier
                            .padding(horizontal = RoundStepperHorizontalInset)
                            .transformedHeight(itemScope, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContentTransformation()
                                    applyContainerTransformation()
                                }
                            }
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    )
                }
            }
            item {
                val itemScope = this
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.interval),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RoundScreenHorizontalPadding)
                            .transformedHeight(itemScope, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContentTransformation()
                                    applyContainerTransformation()
                                }
                            },
                        color = colors.primaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    StepperRow(
                        value = stringResource(R.string.every_minutes, config.intervalMinutes),
                        description = stringResource(R.string.interval_description, config.intervalMinutes),
                        onDecrease = { onConfigChange(WearSetupState(config).withIntervalDelta(-1).config) },
                        onIncrease = { onConfigChange(WearSetupState(config).withIntervalDelta(1).config) },
                        modifier = Modifier
                            .padding(horizontal = RoundStepperHorizontalInset)
                            .transformedHeight(itemScope, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContentTransformation()
                                    applyContainerTransformation()
                                }
                            }
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    )
                }
            }
            item {
                GoldAction(
                    stringResource(R.string.start_session),
                    onClick = onStart,
                    modifier = Modifier
                        .padding(horizontal = RoundActionHorizontalInset)
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContentTransformation()
                                applyContainerTransformation()
                            }
                        }
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                )
            }
            item { Spacer(modifier = Modifier.height(RoundShortcutTopSafeSpace)) }
            item {
                val itemScope = this
                Text(
                    stringResource(R.string.shortcuts),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RoundScreenHorizontalPadding, vertical = 8.dp)
                        .transformedHeight(itemScope, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContentTransformation()
                                applyContainerTransformation()
                            }
                        },
                    color = colors.primaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
            listOf(15, 30, 45, 60, 90, 120, 180).forEach { minutes ->
                item {
                    SoilAction(stringResource(R.string.minutes_value, minutes), stringResource(R.string.set_duration, minutes), onClick = {
                        onConfigChange(WearSetupState(config).withDuration(minutes).config)
                    }, modifier = Modifier
                        .padding(horizontal = RoundActionHorizontalInset, vertical = 2.dp)
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContentTransformation()
                                applyContainerTransformation()
                            }
                        }
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding))
                }
            }
            item { Spacer(modifier = Modifier.height(RoundActionBottomSafeSpace)) }
        }
    }
}

@Composable
private fun StepperRow(
    value: String,
    description: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        SoilAction("−", stringResource(R.string.decrease, description), onDecrease, Modifier.weight(1f))
        Text(
            value,
            modifier = Modifier
                .weight(2f)
                .padding(horizontal = 6.dp)
                .semantics { stateDescription = description },
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        SoilAction("+", stringResource(R.string.increase, description), onIncrease, Modifier.weight(1f))
    }
}
