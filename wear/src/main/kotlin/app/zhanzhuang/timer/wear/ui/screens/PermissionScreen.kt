package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun PermissionScreen(onAllow: () -> Unit, onContinueWithoutHeartRate: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        ScreenColumn {
            Text("Heart rate is optional", color = colors.primaryContainer)
            Text("Allow access to show it during this session.", color = colors.onBackground)
            GoldAction("Allow heart rate", onClick = onAllow)
            SoilAction("Continue without", "Continue without heart rate", onContinueWithoutHeartRate)
        }
    }
}
