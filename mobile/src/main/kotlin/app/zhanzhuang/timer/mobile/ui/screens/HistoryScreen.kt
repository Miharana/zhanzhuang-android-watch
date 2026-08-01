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
import app.zhanzhuang.timer.mobile.ui.HistoryStats
import app.zhanzhuang.timer.model.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(records: List<SessionRecord>, stats: HistoryStats, onOpen: (SessionRecord) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
        Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Today", stats.todayActiveMs)
            Stat("7 days", stats.weekActiveMs)
            Stat("30 days", stats.monthActiveMs)
        }
        if (records.isEmpty()) Text("No sessions yet. Your completed standing practice will appear here.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(records.sortedByDescending { it.startEpochMillis ?: Long.MIN_VALUE }, key = SessionRecord::id) { record ->
                Card(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpen(record) }.semantics { contentDescription = "Open ${record.status.name.lowercase()} session" }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text(date(record)); Text(truthfulStatus(record)) }
                        Text(formatMinutes(record.activeDurationMs), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable private fun Stat(label: String, value: Long) { Column { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatMinutes(value), fontWeight = FontWeight.Bold) } }
private fun date(record: SessionRecord) = record.startEpochMillis?.let { DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) } ?: "Unknown time"
internal fun truthfulStatus(record: SessionRecord) = when (record.status) { app.zhanzhuang.timer.model.SessionStatus.COMPLETED -> "Completed"; app.zhanzhuang.timer.model.SessionStatus.CANCELLED -> "Cancelled"; app.zhanzhuang.timer.model.SessionStatus.INTERRUPTED -> "Interrupted"; else -> record.status.name.lowercase().replaceFirstChar(Char::uppercase) }
internal fun formatMinutes(milliseconds: Long) = "${milliseconds / 60_000L} min"
