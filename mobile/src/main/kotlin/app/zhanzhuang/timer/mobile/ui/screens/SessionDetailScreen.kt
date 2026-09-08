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
import androidx.compose.ui.res.stringResource
import app.zhanzhuang.timer.R
import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SessionDetailScreen(record: SessionRecord, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.session_detail), style = MaterialTheme.typography.headlineMedium)
        Text(truthfulStatus(record), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.active_interval, formatMinutes(record.activeDurationMs), record.config.intervalMinutes))
        Text(stringResource(R.string.owner, stringResource(if (record.owner == app.zhanzhuang.timer.model.SessionOwner.WEAR) R.string.watch else R.string.phone)))
        Text(stringResource(R.string.started, record.startEpochMillis?.let(::localTime) ?: stringResource(R.string.unknown_time)))
        Text(stringResource(R.string.ended, record.endEpochMillis?.let(::localTime) ?: stringResource(R.string.not_ended)))
        Text(stringResource(R.string.paused_duration, formatMinutes(record.pausedDurationMs)))
        Text(heartRateSummary(record.heartRateSamples))
        HeartRateChart(record.heartRateSamples)
    }
}

@Composable
private fun HeartRateChart(samples: List<HeartRateSample>) {
    val description = chartDescription(samples)
    val chartColor = MaterialTheme.colorScheme.primary
    if (samples.size < 2) {
        Text(stringResource(R.string.no_chart), modifier = Modifier.semantics { contentDescription = description })
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

@Composable internal fun chartDescription(samples: List<HeartRateSample>): String = when {
    samples.isEmpty() -> stringResource(R.string.chart_no_samples)
    else -> stringResource(R.string.chart_samples, samples.size, samples.minOf { it.bpm }.toInt(), samples.maxOf { it.bpm }.toInt())
}

@Composable private fun heartRateSummary(samples: List<HeartRateSample>) = when {
    samples.isEmpty() -> stringResource(R.string.heart_rate_not_recorded)
    else -> stringResource(R.string.heart_rate_summary, samples.minOf { it.bpm }.toInt(), samples.maxOf { it.bpm }.toInt(), samples.size)
}

private fun localTime(milliseconds: Long): String = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(milliseconds))
