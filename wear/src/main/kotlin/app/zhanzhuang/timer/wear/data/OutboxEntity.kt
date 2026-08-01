package app.zhanzhuang.timer.wear.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A completed session remains here until the phone confirms its persisted revision. */
@Entity(
    tableName = "wear_sync_outbox",
    foreignKeys = [
        ForeignKey(
            entity = WearSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class OutboxEntity(
    @PrimaryKey val eventId: String,
    val sessionId: String,
    val revision: Long,
    val type: String = TYPE_COMPLETED,
    val payloadVersion: Int = PAYLOAD_VERSION,
    val payload: String,
    val attemptCount: Int = 0,
    val nextAttemptEpochMs: Long = 0,
) {
    companion object {
        const val TYPE_COMPLETED = "COMPLETED"
        const val PAYLOAD_VERSION = 1
    }
}
