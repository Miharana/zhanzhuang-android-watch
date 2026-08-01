package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Text
import app.zhanzhuang.timer.wear.ui.theme.DeepSoil
import app.zhanzhuang.timer.wear.ui.theme.GoldHighlight
import app.zhanzhuang.timer.wear.ui.theme.WarmPaper

@Composable
fun PermissionScreen(onAllow: () -> Unit, onContinueWithoutHeartRate: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(DeepSoil), contentAlignment = Alignment.Center) {
        ScreenColumn {
            Text("Heart rate is optional", color = GoldHighlight)
            Text("Allow access to show it during this session.", color = WarmPaper)
            GoldAction("Allow heart rate", onClick = onAllow)
            SoilAction("Continue without", "Continue without heart rate", onContinueWithoutHeartRate)
        }
    }
}
