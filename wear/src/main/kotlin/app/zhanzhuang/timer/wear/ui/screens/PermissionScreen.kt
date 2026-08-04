package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
import app.zhanzhuang.timer.wear.R

@Composable
fun PermissionScreen(onAllow: () -> Unit, onContinueWithoutHeartRate: () -> Unit, modifier: Modifier = Modifier) {
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
            item { Text(stringResource(R.string.heart_rate_optional), color = colors.primaryContainer) }
            item { Text(stringResource(R.string.heart_rate_permission_info), color = colors.onBackground) }
            item {
                GoldAction(stringResource(R.string.allow_heart_rate), onClick = onAllow, modifier = Modifier.padding(horizontal = RoundActionHorizontalInset))
            }
            item {
                SoilAction(stringResource(R.string.continue_without), stringResource(R.string.continue_without_heart_rate), onContinueWithoutHeartRate, modifier = Modifier.padding(horizontal = RoundActionHorizontalInset))
            }
        }
        ScrollIndicator(state = scroll, modifier = Modifier.align(Alignment.CenterEnd))
    }
}
