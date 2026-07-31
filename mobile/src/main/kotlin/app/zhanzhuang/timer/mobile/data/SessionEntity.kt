package app.zhanzhuang.timer.mobile.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SyncState

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val revision: Long,
    val durationMinutes: Int,
    val intervalMinutes: Int,
    val status: String,
    val owner: String,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val activeDurationMs: Long,
    val pausedDurationMs: Long,
    val healthConnectState: String,
)

internal fun SessionRecord.toEntity() = SessionEntity(
    id = id,
    revision = revision,
    durationMinutes = config.durationMinutes,
    intervalMinutes = config.intervalMinutes,
    status = status.name,
    owner = owner.name,
    startedAtEpochMillis = startEpochMillis,
    endedAtEpochMillis = endEpochMillis,
    activeDurationMs = activeDurationMs,
    pausedDurationMs = pausedDurationMs,
    healthConnectState = healthConnectState.name,
)

internal fun SessionEntity.toRecord(samples: List<HeartRateEntity>) = SessionRecord(
    id = id,
    revision = revision,
    config = SessionConfig(durationMinutes, intervalMinutes),
    status = SessionStatus.valueOf(status),
    owner = SessionOwner.valueOf(owner),
    startEpochMillis = startedAtEpochMillis,
    endEpochMillis = endedAtEpochMillis,
    activeDurationMs = activeDurationMs,
    pausedDurationMs = pausedDurationMs,
    heartRateSamples = samples.map { it.toSample() },
    healthConnectState = SyncState.valueOf(healthConnectState),
)
