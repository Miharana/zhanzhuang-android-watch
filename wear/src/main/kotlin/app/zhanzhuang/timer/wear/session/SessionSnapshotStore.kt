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
    /** At most one Room append batch; aggregate history deliberately lives only in Room. */
    val pendingHeartRateSamples: List<HeartRateSample> = emptyList(),
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
        // Aggregate history belongs to Room. A snapshot carries only the retryable append tail.
        put("samples", JSONArray())
        put("pendingHeartRateSamples", JSONArray(pendingHeartRateSamples.takeLast(MAX_PENDING_SAMPLES).map { sample ->
            JSONObject().apply {
                put("epochMillis", sample.epochMillis)
                put("bpm", sample.bpm)
                put("accuracy", sample.accuracy.name)
            }
        }))
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
            pendingHeartRateSamples = optJSONArray("pendingHeartRateSamples")?.let { array ->
                List(array.length().coerceAtMost(MAX_PENDING_SAMPLES)) { index -> array.getJSONObject(index).let { value ->
                    HeartRateSample(value.getLong("epochMillis"), value.getDouble("bpm"), SampleAccuracy.valueOf(value.getString("accuracy")))
                } }
            } ?: emptyList(),
        )
    }

    private fun JSONObject.getLongOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)

    private companion object {
        const val KEY = WearSessionRecoveryPolicy.SNAPSHOT_KEY
        const val MAX_PENDING_SAMPLES = 20
    }
}
