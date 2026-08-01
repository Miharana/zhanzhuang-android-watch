package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.Text
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import app.zhanzhuang.timer.wear.ui.formatWearDuration
import app.zhanzhuang.timer.wear.ui.theme.DeepSoil
import app.zhanzhuang.timer.wear.ui.theme.GoldHighlight
import app.zhanzhuang.timer.wear.ui.theme.WarmPaper

@Composable
fun CompletionScreen(state: WearSessionUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(DeepSoil), contentAlignment = Alignment.Center) {
        ScreenColumn {
            Text("Complete", color = GoldHighlight, fontWeight = FontWeight.Bold)
            Text(formatWearDuration(state.record?.activeDurationMs ?: 0L), color = WarmPaper)
            state.currentHeartRateBpm?.let { Text("Last heart rate ${it.toInt()} bpm", color = GoldHighlight) }
            GoldAction("Done", onClick = onDone)
        }
    }
}
