package app.zhanzhuang.timer.wear.session

import android.content.SharedPreferences
import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SampleAccuracy
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import org.json.JSONArray
import org.json.JSONObject

/** Durable process-recovery state. The boot ID prevents unsafe elapsed-time reconstruction after reboot. */
data class WearSessionSnapshot(
    val record: SessionRecord,
    val bootId: String,
    val runningSinceBootMs: Long? = null,
    val nextReminderAtActiveMs: Long? = null,
    val healthExerciseOwned: Boolean = false,
    val pendingOperation: WearExternalOperation? = null,
    val pausedSinceBootMs: Long? = null,
    val intendedTerminalStatus: SessionStatus? = null,
    val heartRateAcceptFromBootMs: Long? = null,
)

enum class WearExternalOperation { START, PAUSE, RESUME, END }

interface SessionSnapshotStore {
    suspend fun save(snapshot: WearSessionSnapshot)
    suspend fun load(): WearSessionSnapshot?
}

class SharedPreferencesSessionSnapshotStore(
    private val preferences: SharedPreferences,
) : SessionSnapshotStore {
    override suspend fun save(snapshot: WearSessionSnapshot) {
        check(preferences.edit().putString(KEY, snapshot.toJson().toString()).commit()) { "Wear session snapshot commit failed" }
    }

    override suspend fun load(): WearSessionSnapshot? = preferences.getString(KEY, null)?.let { encoded ->
        runCatching { JSONObject(encoded).toSnapshot() }.getOrNull()
    }

    private fun WearSessionSnapshot.toJson() = JSONObject().apply {
        put("bootId", bootId)
        put("runningSinceBootMs", runningSinceBootMs)
        put("nextReminderAtActiveMs", nextReminderAtActiveMs)
        put("healthExerciseOwned", healthExerciseOwned)
        put("pendingOperation", pendingOperation?.name)
        put("pausedSinceBootMs", pausedSinceBootMs)
        put("intendedTerminalStatus", intendedTerminalStatus?.name)
        put("heartRateAcceptFromBootMs", heartRateAcceptFromBootMs)
        put("id", record.id)
        put("revision", record.revision)
        put("durationMinutes", record.config.durationMinutes)
        put("intervalMinutes", record.config.intervalMinutes)
        put("status", record.status.name)
        put("startEpochMillis", record.startEpochMillis)
        put("endEpochMillis", record.endEpochMillis)
        put("activeDurationMs", record.activeDurationMs)
        put("pausedDurationMs", record.pausedDurationMs)
        // Samples are batched to Room; snapshots intentionally never rewrite a growing HR history.
        put("samples", JSONArray())
    }

    private fun JSONObject.toSnapshot(): WearSessionSnapshot {
        val samples = getJSONArray("samples").let { array ->
            List(array.length()) { index -> array.getJSONObject(index).let { value ->
                HeartRateSample(
                    epochMillis = value.getLong("epochMillis"),
                    bpm = value.getDouble("bpm"),
                    accuracy = SampleAccuracy.valueOf(value.getString("accuracy")),
                )
            } }
        }
        return WearSessionSnapshot(
            record = SessionRecord(
                id = getString("id"),
                revision = getLong("revision"),
                config = SessionConfig(getInt("durationMinutes"), getInt("intervalMinutes")),
                status = SessionStatus.valueOf(getString("status")),
                owner = SessionOwner.WEAR,
                startEpochMillis = getLongOrNull("startEpochMillis"),
                endEpochMillis = getLongOrNull("endEpochMillis"),
                activeDurationMs = getLong("activeDurationMs"),
                pausedDurationMs = getLong("pausedDurationMs"),
                heartRateSamples = samples,
            ),
            bootId = getString("bootId"),
            runningSinceBootMs = getLongOrNull("runningSinceBootMs"),
            nextReminderAtActiveMs = getLongOrNull("nextReminderAtActiveMs"),
            healthExerciseOwned = optBoolean("healthExerciseOwned", false),
            pendingOperation = optString("pendingOperation").takeIf { it.isNotEmpty() }?.let(WearExternalOperation::valueOf),
            pausedSinceBootMs = getLongOrNull("pausedSinceBootMs"),
            intendedTerminalStatus = optString("intendedTerminalStatus").takeIf { it.isNotEmpty() }?.let(SessionStatus::valueOf),
            heartRateAcceptFromBootMs = getLongOrNull("heartRateAcceptFromBootMs"),
        )
    }

    private fun JSONObject.getLongOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)

    private companion object { const val KEY = "wear_session_snapshot_v1" }
}
