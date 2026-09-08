package app.zhanzhuang.timer.wear.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import app.zhanzhuang.timer.wear.ui.theme.GoldHighlight
import app.zhanzhuang.timer.wear.ui.theme.GoldSheen
import app.zhanzhuang.timer.wear.ui.theme.Sparkle

/** A one-shot, low-cost glint. Callers gate it for ambient, power-save and reduced-motion. */
@Composable
fun GoldSparkle(enabled: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val progress = androidx.compose.runtime.remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 360, easing = LinearEasing))
            onFinished()
        } else {
            progress.snapTo(0f)
        }
    }
    if (progress.value > 0f) Canvas(modifier.fillMaxSize()) {
        val alpha = (1f - progress.value).coerceIn(0f, 1f)
        val radius = size.minDimension * (0.014f + progress.value * 0.026f)
        listOf(Offset(size.width * .24f, size.height * .28f), Offset(size.width * .73f, size.height * .40f), Offset(size.width * .51f, size.height * .70f))
            .forEachIndexed { index, offset ->
                drawCircle(if (index == 1) GoldHighlight.copy(alpha = alpha) else Sparkle.copy(alpha = alpha), radius, offset)
                drawCircle(GoldSheen.copy(alpha = alpha * .65f), radius * .35f, offset)
            }
    }
}
