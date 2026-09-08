package app.zhanzhuang.timer.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.zhanzhuang.timer.mobile.ui.theme.DeepGold
import app.zhanzhuang.timer.mobile.ui.theme.Gold
import app.zhanzhuang.timer.mobile.ui.theme.PaleGold
import app.zhanzhuang.timer.mobile.ui.theme.TimerSoil

/** Static E mark for navigation chrome; the live timer uses [EIntervalTimer]. */
@Composable
fun EBrandMark(
    description: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(64.dp)
            .semantics { contentDescription = description },
    ) {
        val edge = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val rimRadius = edge * .43f
        val top = Offset(center.x, center.y - edge * .17f)
        val taijiRadius = edge * .14f
        val lowerY = center.y + edge * .17f
        val lowerRadius = edge * .078f

        drawCircle(TimerSoil, radius = edge / 2f, center = center)
        drawCircle(DeepGold, radius = rimRadius, center = center, style = Stroke(edge * .018f))
        drawCircle(PaleGold.copy(alpha = .72f), radius = rimRadius, center = center, style = Stroke(edge * .005f))

        drawCircle(Gold, radius = taijiRadius, center = top)
        drawArc(
            color = TimerSoil,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(top.x - taijiRadius, top.y - taijiRadius),
            size = Size(taijiRadius * 2f, taijiRadius * 2f),
        )
        drawCircle(TimerSoil, radius = taijiRadius / 2f, center = Offset(top.x, top.y - taijiRadius / 2f))
        drawCircle(Gold, radius = taijiRadius / 2f, center = Offset(top.x, top.y + taijiRadius / 2f))
        drawCircle(Gold, radius = taijiRadius * .15f, center = Offset(top.x, top.y - taijiRadius / 2f))
        drawCircle(TimerSoil, radius = taijiRadius * .15f, center = Offset(top.x, top.y + taijiRadius / 2f))

        listOf(center.x - edge * .115f, center.x + edge * .115f).forEach { x ->
            drawCircle(DeepGold, radius = lowerRadius, center = Offset(x, lowerY), style = Stroke(lowerRadius * .19f))
            drawCircle(PaleGold.copy(alpha = .7f), radius = lowerRadius, center = Offset(x, lowerY), style = Stroke(lowerRadius * .055f))
        }
    }
}
