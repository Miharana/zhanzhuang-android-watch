package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import app.zhanzhuang.timer.wear.R

@Composable
fun PermissionScreen(onAllow: () -> Unit, onContinueWithoutHeartRate: () -> Unit, modifier: Modifier = Modifier) {
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
                Text(
                    stringResource(R.string.heart_rate_optional),
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
                val itemScope = this
                Text(
                    stringResource(R.string.heart_rate_permission_info),
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
                    color = colors.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                GoldAction(
                    stringResource(R.string.allow_heart_rate),
                    onClick = onAllow,
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
                    stringResource(R.string.continue_without),
                    stringResource(R.string.continue_without_heart_rate),
                    onContinueWithoutHeartRate,
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
