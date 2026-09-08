package app.zhanzhuang.timer.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.zhanzhuang.timer.R
import app.zhanzhuang.timer.mobile.ui.HistoryStats
import app.zhanzhuang.timer.model.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(records: List<SessionRecord>, stats: HistoryStats, onOpen: (SessionRecord) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.today), stats.todayActiveMs)
            Stat(stringResource(R.string.seven_days), stats.weekActiveMs)
            Stat(stringResource(R.string.thirty_days), stats.monthActiveMs)
        }
        if (records.isEmpty()) Text(stringResource(R.string.no_sessions))
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(records.sortedByDescending { it.startEpochMillis ?: Long.MIN_VALUE }, key = SessionRecord::id) { record ->
                val status = truthfulStatus(record)
                val openDescription = stringResource(R.string.open_session, status)
                Card(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpen(record) }.semantics { contentDescription = openDescription }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text(date(record)); Text(status) }
                        Text(formatMinutes(record.activeDurationMs), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable private fun Stat(label: String, value: Long) { Column { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatMinutes(value), fontWeight = FontWeight.Bold) } }
@Composable private fun date(record: SessionRecord) = record.startEpochMillis?.let { DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) } ?: stringResource(R.string.unknown_time)
@Composable internal fun truthfulStatus(record: SessionRecord) = when (record.status) {
    app.zhanzhuang.timer.model.SessionStatus.IDLE -> stringResource(R.string.status_idle)
    app.zhanzhuang.timer.model.SessionStatus.STARTING -> stringResource(R.string.status_starting)
    app.zhanzhuang.timer.model.SessionStatus.RUNNING -> stringResource(R.string.status_running)
    app.zhanzhuang.timer.model.SessionStatus.PAUSED -> stringResource(R.string.status_paused)
    app.zhanzhuang.timer.model.SessionStatus.COMPLETING -> stringResource(R.string.status_completing)
    app.zhanzhuang.timer.model.SessionStatus.COMPLETED -> stringResource(R.string.status_completed)
    app.zhanzhuang.timer.model.SessionStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    app.zhanzhuang.timer.model.SessionStatus.INTERRUPTED -> stringResource(R.string.status_interrupted)
}
@Composable internal fun formatMinutes(milliseconds: Long) = stringResource(R.string.minutes_value, milliseconds / 60_000L)
