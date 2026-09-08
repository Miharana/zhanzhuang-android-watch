package app.zhanzhuang.timer.model

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class SessionConfig(
    val durationMinutes: Int = 30,
    val intervalMinutes: Int = 10,
) {
    init {
        require(durationMinutes in 15..180 && durationMinutes % 5 == 0)
        require(intervalMinutes in setOf(5, 10, 15, 20, 30))
    }
}

@Serializable
enum class SessionStatus { IDLE, STARTING, RUNNING, PAUSED, COMPLETING, COMPLETED, CANCELLED, INTERRUPTED }

@Serializable
enum class SessionOwner { MOBILE, WEAR }

@Serializable
enum class SampleAccuracy { LOW, MEDIUM, HIGH }

@Serializable
enum class SyncState { LOCAL_ONLY, PENDING, SYNCED, FAILED }

@Serializable
data class HeartRateSample(
    val epochMillis: Long,
    val bpm: Double,
    val accuracy: SampleAccuracy,
)

@Serializable
data class SessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val revision: Long = 0,
    val config: SessionConfig = SessionConfig(),
    val status: SessionStatus = SessionStatus.IDLE,
    val owner: SessionOwner = SessionOwner.MOBILE,
    val startEpochMillis: Long? = null,
    val endEpochMillis: Long? = null,
    val activeDurationMs: Long = 0,
    val pausedDurationMs: Long = 0,
    val heartRateSamples: List<HeartRateSample> = emptyList(),
    val healthConnectState: SyncState = SyncState.LOCAL_ONLY,
)
