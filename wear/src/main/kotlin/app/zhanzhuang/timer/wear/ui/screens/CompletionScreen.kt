package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
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
    val colors = MaterialTheme.colorScheme
    val statusLabel = when (state.record?.status) {
        SessionStatus.COMPLETED -> stringResource(R.string.terminal_completed)
        SessionStatus.CANCELLED -> stringResource(R.string.terminal_cancelled)
        SessionStatus.INTERRUPTED -> stringResource(R.string.terminal_interrupted)
        else -> stringResource(R.string.end_session)
    }
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
            item { Text(statusLabel, color = colors.primaryContainer, fontWeight = FontWeight.Bold) }
            item { Text(formatWearDuration(state.record?.activeDurationMs ?: 0L), color = colors.onBackground) }
            state.currentHeartRateBpm?.let { bpm ->
                item { Text(stringResource(R.string.last_heart_rate, bpm.toInt()), color = colors.primaryContainer) }
            }
            item {
                GoldAction(
                    stringResource(R.string.done),
                    onClick = onDone,
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
        }
    }
}
