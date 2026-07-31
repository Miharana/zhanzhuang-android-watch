package app.zhanzhuang.timer.mobile.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

data class MobileRuntimeSnapshot(
    val sessionId: String,
    val startedAtElapsedMs: Long,
    val checkpointElapsedMs: Long,
    val checkpointWallEpochMs: Long,
    val pausedAtElapsedMs: Long?,
    val acknowledgedReminderIndex: Int,
)

@Entity(
    tableName = "mobile_runtime",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class MobileRuntimeEntity(
    @PrimaryKey val singletonId: Int = SINGLETON_ID,
    val sessionId: String,
    val startedAtElapsedMs: Long,
    val checkpointElapsedMs: Long,
    val checkpointWallEpochMs: Long,
    val pausedAtElapsedMs: Long?,
    val acknowledgedReminderIndex: Int,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

internal fun MobileRuntimeSnapshot.toEntity() = MobileRuntimeEntity(
    sessionId = sessionId,
    startedAtElapsedMs = startedAtElapsedMs,
    checkpointElapsedMs = checkpointElapsedMs,
    checkpointWallEpochMs = checkpointWallEpochMs,
    pausedAtElapsedMs = pausedAtElapsedMs,
    acknowledgedReminderIndex = acknowledgedReminderIndex,
)

internal fun MobileRuntimeEntity.toSnapshot() = MobileRuntimeSnapshot(
    sessionId = sessionId,
    startedAtElapsedMs = startedAtElapsedMs,
    checkpointElapsedMs = checkpointElapsedMs,
    checkpointWallEpochMs = checkpointWallEpochMs,
    pausedAtElapsedMs = pausedAtElapsedMs,
    acknowledgedReminderIndex = acknowledgedReminderIndex,
)
