package app.zhanzhuang.timer.mobile.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SampleAccuracy

@Entity(
    tableName = "heart_rate_samples",
    primaryKeys = ["sessionId", "epochMillis"],
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
data class HeartRateEntity(
    val sessionId: String,
    val epochMillis: Long,
    val bpm: Double,
    val accuracy: String,
)

internal fun HeartRateEntity.toSample() = HeartRateSample(
    epochMillis = epochMillis,
    bpm = bpm,
    accuracy = SampleAccuracy.valueOf(accuracy),
)

internal fun HeartRateSample.toEntity(sessionId: String) = HeartRateEntity(
    sessionId = sessionId,
    epochMillis = epochMillis,
    bpm = bpm,
    accuracy = accuracy.name,
)
