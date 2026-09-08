package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.window.Dialog
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import app.zhanzhuang.timer.wear.ui.formatWearDuration
import app.zhanzhuang.timer.wear.R
import app.zhanzhuang.timer.wear.ui.components.EIntervalTimer

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
                Text(
                    if (paused) stringResource(R.string.paused) else stringResource(R.string.standing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RoundScreenHorizontalPadding, vertical = 6.dp)
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
            item {
                EIntervalTimer(
                    activeElapsedMs = state.record?.let { record ->
                        (record.config.durationMinutes * 60_000L - state.remainingMs).coerceAtLeast(0)
                    } ?: 0L,
                    config = state.record?.config ?: app.zhanzhuang.timer.model.SessionConfig(),
                    description = remainingDescription,
                    ambient = ambient,
                    modifier = Modifier
                        .padding(horizontal = RoundActionHorizontalInset)
                        .transformedHeight(this, transformationSpec),
                )
            }
            item {
                val itemScope = this
                Text(
                    text = if (ambient) stringResource(R.string.minutes_value, (state.remainingMs + 59_999L) / 60_000L) else formatWearDuration(state.remainingMs),
                    color = colors.onBackground,
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RoundScreenHorizontalPadding, vertical = 6.dp)
                        .semantics { stateDescription = remainingDescription }
                        .transformedHeight(itemScope, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContentTransformation()
                                applyContainerTransformation()
                            }
                        },
                )
            }
            state.currentHeartRateBpm?.let { bpm ->
                item {
                    val itemScope = this
                    Text(
                        stringResource(R.string.last_heart_rate, bpm.toInt()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RoundScreenHorizontalPadding, vertical = 6.dp)
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
            }
            state.heartRateUnavailableReason?.let {
                item {
                    val itemScope = this
                    Text(
                        stringResource(R.string.duration_only),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RoundScreenHorizontalPadding, vertical = 6.dp)
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
            }
            item {
                val label = if (paused) stringResource(R.string.resume_session) else stringResource(R.string.pause_session)
                val actionModifier = Modifier
                    .padding(horizontal = RoundActionHorizontalInset)
                    .transformedHeight(this, transformationSpec)
                    .graphicsLayer {
                        with(SurfaceTransformation(transformationSpec)) {
                            applyContentTransformation()
                            applyContainerTransformation()
                        }
                    }
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                if (paused) GoldAction(label, onClick = onResume, modifier = actionModifier) else SoilAction(label, onClick = onPause, modifier = actionModifier)
            }
            item {
                SoilAction(
                    stringResource(R.string.end_session),
                    stringResource(R.string.end_session),
                    onClick = { confirmingFinish = true },
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
            item { Spacer(modifier = Modifier.height(RoundActionBottomSafeSpace)) }
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
    val transformationSpec = rememberTransformationSpec()
    val scroll = rememberTransformingLazyColumnState()
    Dialog(onDismissRequest = onDismiss) {
        ScreenScaffold(
            scrollState = scroll,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) { contentPadding ->
            TransformingLazyColumn(
                state = scroll,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = scroll),
                rotaryScrollableBehavior = RotaryScrollableDefaults.snapBehavior(scrollableState = scroll),
            ) {
                item {
                    val itemScope = this
                    Text(
                        stringResource(R.string.end_session_question),
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
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    GoldAction(
                        stringResource(R.string.end_session),
                        stringResource(R.string.confirm_end_session),
                        onClick = onConfirm,
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
                item {
                    SoilAction(
                        stringResource(R.string.cancel_session),
                        stringResource(R.string.cancel_session),
                        onClick = onCancel,
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
                item {
                    SoilAction(
                        stringResource(R.string.keep_standing),
                        onClick = onDismiss,
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
                item { Spacer(modifier = Modifier.height(RoundActionBottomSafeSpace)) }
            }
        }
    }
}
