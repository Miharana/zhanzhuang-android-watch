package app.zhanzhuang.timer.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SessionDetailScreen(record: SessionRecord, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
        Text("Session detail", style = MaterialTheme.typography.headlineMedium)
        Text(truthfulStatus(record), style = MaterialTheme.typography.titleLarge)
        Text("${formatMinutes(record.activeDurationMs)} active · ${record.config.intervalMinutes}-minute interval")
        Text("Owner: ${if (record.owner == app.zhanzhuang.timer.model.SessionOwner.WEAR) "Watch" else "Phone"}")
        Text("Started: ${record.startEpochMillis?.let(::localTime) ?: "Unknown"}")
        Text("Ended: ${record.endEpochMillis?.let(::localTime) ?: "Not ended"}")
        Text("Paused: ${formatMinutes(record.pausedDurationMs)}")
        Text(heartRateSummary(record.heartRateSamples))
        HeartRateChart(record.heartRateSamples)
    }
}

@Composable
private fun HeartRateChart(samples: List<HeartRateSample>) {
    val description = chartDescription(samples)
    val chartColor = MaterialTheme.colorScheme.primary
    if (samples.size < 2) {
        Text("No heart-rate chart available", modifier = Modifier.semantics { contentDescription = description })
        return
    }
    Canvas(
        modifier = Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description },
    ) {
        val minimum = samples.minOf { it.bpm }.toFloat()
        val maximum = samples.maxOf { it.bpm }.toFloat()
        val range = (maximum - minimum).takeIf { it > 0f } ?: 1f
        val start = samples.first().epochMillis
        val duration = (samples.last().epochMillis - start).takeIf { it > 0L } ?: 1L
        val path = Path()
        samples.forEachIndexed { index, sample ->
            val x = ((sample.epochMillis - start).toFloat() / duration) * size.width
            val y = size.height - ((sample.bpm.toFloat() - minimum) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = chartColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    }
}

internal fun chartDescription(samples: List<HeartRateSample>): String = when {
    samples.isEmpty() -> "Heart-rate chart: no samples"
    else -> "Heart-rate chart: ${samples.size} samples, ${samples.minOf { it.bpm }.toInt()} to ${samples.maxOf { it.bpm }.toInt()} bpm"
}

private fun heartRateSummary(samples: List<HeartRateSample>) = when {
    samples.isEmpty() -> "Heart rate: not recorded"
    else -> "Heart rate: ${samples.minOf { it.bpm }.toInt()}–${samples.maxOf { it.bpm }.toInt()} bpm, ${samples.size} samples"
}

private fun localTime(milliseconds: Long): String = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(milliseconds))
