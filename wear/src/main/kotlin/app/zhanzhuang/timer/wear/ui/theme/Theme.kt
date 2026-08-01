package app.zhanzhuang.timer.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/** Wear Material 3 supplies native type and interaction behavior; screens consume the earth/gold roles above. */
@Composable
fun ZhanZhuangWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
