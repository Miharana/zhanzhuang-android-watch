package app.zhanzhuang.timer.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import app.zhanzhuang.timer.mobile.ui.theme.Gold
import app.zhanzhuang.timer.mobile.ui.theme.PaleGold
import app.zhanzhuang.timer.mobile.ui.theme.Sparkle

/** One short glint, deliberately gated by the activity for power saver and reduced motion. */
@Composable
fun GoldSparkle(enabled: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 360, easing = LinearEasing))
            onFinished()
        } else progress.snapTo(0f)
    }
    if (progress.value > 0f) Canvas(modifier.fillMaxSize()) {
        val alpha = (1f - progress.value).coerceIn(0f, 1f)
        val radius = size.minDimension * (0.012f + progress.value * 0.018f)
        listOf(Offset(size.width * .22f, size.height * .24f), Offset(size.width * .78f, size.height * .38f), Offset(size.width * .52f, size.height * .72f))
            .forEachIndexed { index, offset ->
                drawCircle(if (index == 1) PaleGold.copy(alpha = alpha) else Sparkle.copy(alpha = alpha), radius, offset)
                drawCircle(Gold.copy(alpha = alpha * .55f), radius * .36f, offset)
            }
    }
}
