package app.zhanzhuang.timer.wear.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import app.zhanzhuang.timer.domain.intervalProgress
import app.zhanzhuang.timer.model.SessionConfig
import androidx.wear.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Round-safe E timer with one reflective revolution for each reminder interval. */
@Composable
fun EIntervalTimer(
    activeElapsedMs: Long,
    config: SessionConfig,
    description: String,
    ambient: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val progress = intervalProgress(activeElapsedMs, config)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { stateDescription = description },
    ) {
        val edge = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringRadius = edge * .44f
        val ringStroke = edge * .018f
        val top = Offset(center.x, center.y - edge * .18f)
        val taijiRadius = edge * .135f
        val lowerRadius = edge * .076f
        val lowerY = center.y + edge * .17f

        drawCircle(colors.background, radius = edge / 2f, center = center)
        drawCircle(colors.primary.copy(alpha = .22f), radius = ringRadius, center = center, style = Stroke(ringStroke))
        if (progress > 0f) {
            drawArc(
                color = colors.primary,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(ringStroke),
            )
            if (!ambient) {
                val angle = progress * (2f * PI.toFloat()) - PI.toFloat() / 2f
                val glint = Offset(center.x + ringRadius * cos(angle), center.y + ringRadius * sin(angle))
                drawCircle(colors.primaryContainer, radius = ringStroke * 1.15f, center = glint)
                drawCircle(Color.White.copy(alpha = .74f), radius = ringStroke * .34f, center = glint)
            }
        }

        drawTaiji(top, taijiRadius, colors.primary, colors.background)
        drawRing(Offset(center.x - edge * .115f, lowerY), lowerRadius, colors.primaryDim, colors.primaryContainer)
        drawRing(Offset(center.x + edge * .115f, lowerY), lowerRadius, colors.primaryDim, colors.primaryContainer)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTaiji(center: Offset, radius: Float, gold: Color, soil: Color) {
    drawCircle(gold, radius, center)
    drawArc(soil, 90f, 180f, useCenter = true, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2f, radius * 2f))
    drawCircle(soil, radius / 2f, Offset(center.x, center.y - radius / 2f))
    drawCircle(gold, radius / 2f, Offset(center.x, center.y + radius / 2f))
    drawCircle(gold, radius * .15f, Offset(center.x, center.y - radius / 2f))
    drawCircle(soil, radius * .15f, Offset(center.x, center.y + radius / 2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(center: Offset, radius: Float, rim: Color, highlight: Color) {
    drawCircle(rim, radius, center, style = Stroke(radius * .19f))
    drawCircle(highlight.copy(alpha = .7f), radius, center, style = Stroke(radius * .055f))
}
