package app.zhanzhuang.timer.mobile.ui.components

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
import app.zhanzhuang.timer.mobile.ui.theme.DeepGold
import app.zhanzhuang.timer.mobile.ui.theme.Gold
import app.zhanzhuang.timer.mobile.ui.theme.PaleGold
import app.zhanzhuang.timer.mobile.ui.theme.TimerSoil
import app.zhanzhuang.timer.model.SessionConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The E mark becomes the live timer: its outer rim completes once per reminder interval. */
@Composable
fun EIntervalTimer(
    activeElapsedMs: Long,
    config: SessionConfig,
    description: String,
    modifier: Modifier = Modifier,
) {
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

        drawCircle(TimerSoil, radius = edge / 2f, center = center)
        drawCircle(Gold.copy(alpha = .22f), radius = ringRadius, center = center, style = Stroke(ringStroke))
        if (progress > 0f) {
            drawArc(
                color = Gold,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(ringStroke),
            )
            val angle = progress * (2f * PI.toFloat()) - PI.toFloat() / 2f
            val glint = Offset(center.x + ringRadius * cos(angle), center.y + ringRadius * sin(angle))
            drawCircle(PaleGold, radius = ringStroke * 1.15f, center = glint)
            drawCircle(Color.White.copy(alpha = .74f), radius = ringStroke * .34f, center = glint)
        }

        drawTaiji(top, taijiRadius, TimerSoil)
        drawRing(Offset(center.x - edge * .115f, lowerY), lowerRadius)
        drawRing(Offset(center.x + edge * .115f, lowerY), lowerRadius)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTaiji(center: Offset, radius: Float, soil: Color) {
    drawCircle(Gold, radius, center)
    drawArc(soil, 90f, 180f, useCenter = true, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2f, radius * 2f))
    drawCircle(soil, radius / 2f, Offset(center.x, center.y - radius / 2f))
    drawCircle(Gold, radius / 2f, Offset(center.x, center.y + radius / 2f))
    drawCircle(Gold, radius * .15f, Offset(center.x, center.y - radius / 2f))
    drawCircle(soil, radius * .15f, Offset(center.x, center.y + radius / 2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(center: Offset, radius: Float) {
    drawCircle(Color.Transparent, radius, center)
    drawCircle(DeepGold, radius, center, style = Stroke(radius * .19f))
    drawCircle(PaleGold.copy(alpha = .7f), radius, center, style = Stroke(radius * .055f))
}
