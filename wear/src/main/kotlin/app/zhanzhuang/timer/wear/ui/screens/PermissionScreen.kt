package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import app.zhanzhuang.timer.wear.R

@Composable
fun PermissionScreen(onAllow: () -> Unit, onContinueWithoutHeartRate: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        ScreenColumn(verticalSpacing = 4.dp) {
            Spacer(Modifier.height(36.dp))
            Text(stringResource(R.string.heart_rate_optional), color = colors.primaryContainer)
            Text(stringResource(R.string.heart_rate_permission_info), color = colors.onBackground)
            GoldAction(stringResource(R.string.allow_heart_rate), onClick = onAllow, modifier = Modifier.padding(horizontal = RoundActionHorizontalInset))
            SoilAction(stringResource(R.string.continue_without), stringResource(R.string.continue_without_heart_rate), onContinueWithoutHeartRate, modifier = Modifier.padding(horizontal = RoundActionHorizontalInset))
            Spacer(Modifier.height(RoundActionBottomSafeSpace))
        }
    }
}
