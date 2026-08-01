package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme

internal val RoundScreenHorizontalPadding = 18.dp
internal val RoundActionHorizontalInset = 32.dp
internal val RoundActionBottomSafeSpace = 64.dp
internal val RoundScreenPadding = PaddingValues(horizontal = RoundScreenHorizontalPadding, vertical = 16.dp)

/** Geometry behind the 454px round Wear target used for release screenshots and interaction layout. */
internal object RoundScreenGeometry {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun actionBounds(diameterPx: Int, density: Float, topPx: Int): Rect {
        val horizontalInset = ((RoundScreenHorizontalPadding.value + RoundActionHorizontalInset.value) * density).roundToInt()
        val actionHeight = (48f * density).roundToInt()
        return Rect(horizontalInset, topPx, diameterPx - horizontalInset, topPx + actionHeight)
    }

    fun rectFitsCircle(rect: Rect, diameterPx: Int): Boolean {
        val center = diameterPx / 2.0
        val radiusSquared = center * center
        return listOf(
            rect.left.toDouble() to rect.top.toDouble(),
            rect.left.toDouble() to rect.bottom.toDouble(),
            rect.right.toDouble() to rect.top.toDouble(),
            rect.right.toDouble() to rect.bottom.toDouble(),
        ).all { (x, y) ->
            val dx = x - center
            val dy = y - center
            dx * dx + dy * dy <= radiusSquared
        }
    }
}

@Composable
internal fun ScreenColumn(
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    verticalSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(scrollState)
            .padding(RoundScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

@Composable
internal fun GoldAction(
    label: String,
    contentDescription: String = label,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val color = if (enabled) colors.primary else colors.primaryDim
    Box(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .semantics { this.contentDescription = contentDescription; role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SoilAction(label: String, contentDescription: String = label, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outline, RoundedCornerShape(24.dp))
            .semantics { this.contentDescription = contentDescription; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.onSurface)
    }
}
