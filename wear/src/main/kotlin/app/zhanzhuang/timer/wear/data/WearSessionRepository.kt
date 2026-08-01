package app.zhanzhuang.timer.wear.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SampleAccuracy
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SyncState

@Entity(tableName = "wear_sessions")
data class WearSessionEntity(
    @PrimaryKey val id: String,
    val revision: Long,
    val durationMinutes: Int,
    val intervalMinutes: Int,
    val status: String,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val activeDurationMs: Long,
    val pausedDurationMs: Long,
)

@Entity(
    tableName = "wear_heart_rate_samples",
    primaryKeys = ["sessionId", "epochMillis"],
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
data class WearHeartRateEntity(
    val sessionId: String,
    val epochMillis: Long,
    val bpm: Double,
    val accuracy: String,
)

data class WearSessionWithSamples(
    @Embedded val session: WearSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val samples: List<WearHeartRateEntity>,
)

@Dao
interface WearSessionDao {
    @Query("SELECT * FROM wear_sessions WHERE status IN ('STARTING', 'RUNNING', 'PAUSED', 'COMPLETING') ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun activeSession(): WearSessionEntity?
    @Query("SELECT * FROM wear_sessions WHERE id = :id")
    suspend fun session(id: String): WearSessionEntity?

    @Query("SELECT * FROM wear_heart_rate_samples WHERE sessionId = :sessionId ORDER BY epochMillis")
    suspend fun samples(sessionId: String): List<WearHeartRateEntity>

    @Transaction
    @Query("SELECT * FROM wear_sessions WHERE id = :id")
    suspend fun sessionWithSamples(id: String): WearSessionWithSamples?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(entity: WearSessionEntity): Long

    @Update
    suspend fun updateSession(entity: WearSessionEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveSamples(samples: List<WearHeartRateEntity>)

    @Query("SELECT * FROM wear_sync_outbox ORDER BY nextAttemptEpochMs, eventId")
    suspend fun pendingOutbox(): List<OutboxEntity>

    @Query("SELECT * FROM wear_sync_outbox WHERE eventId = :eventId")
    suspend fun outbox(eventId: String): OutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOutbox(entity: OutboxEntity)

    @Query("DELETE FROM wear_sync_outbox WHERE eventId = :eventId AND revision = :acknowledgedRevision")
    suspend fun deleteAcknowledged(eventId: String, acknowledgedRevision: Long)

    @Transaction
    suspend fun checkpoint(record: SessionRecord, samples: List<HeartRateSample>) {
        val existing = session(record.id)
        when {
            existing == null -> insertSession(record.toWearEntity())
            record.revision < existing.revision -> return
            record.revision > existing.revision -> updateSession(record.toWearEntity())
            // Equal revisions intentionally retain metadata while allowing a crash-safe sample replay.
        }
        if (samples.isNotEmpty()) {
            saveSamples(samples.map { it.toWearEntity(record.id) })
        }
    }

    @Transaction
    suspend fun enqueueIfCurrent(entity: OutboxEntity) {
        val currentSession = session(entity.sessionId) ?: return
        if (entity.revision != currentSession.revision) return

        val existing = outbox(entity.eventId)
        if (existing != null && entity.revision <= existing.revision) return
        saveOutbox(entity)
    }

    /** Completion and its transferable event are one crash-atomic durable decision. */
    @Transaction
    suspend fun saveCompletedWithOutbox(record: SessionRecord, samples: List<HeartRateSample>, entity: OutboxEntity) {
        checkpoint(record, samples)
        enqueueIfCurrent(entity)
    }
}

class WearSessionRepository(private val dao: WearSessionDao) {
    constructor(database: WearDatabase) : this(database.wearSessionDao())

    suspend fun get(id: String): SessionRecord? = dao.sessionWithSamples(id)?.let { aggregate ->
        aggregate.session.toRecord(aggregate.samples.sortedBy(WearHeartRateEntity::epochMillis))
    }

    suspend fun activeSession(): SessionRecord? = dao.activeSession()?.let { session ->
        session.toRecord(dao.samples(session.id))
    }

    suspend fun upsert(record: SessionRecord) {
        require(record.owner == SessionOwner.WEAR) { "Wear can only persist Wear-owned sessions" }
        dao.checkpoint(record, record.heartRateSamples)
    }

    suspend fun enqueueCompleted(
        eventId: String,
        sessionId: String,
        revision: Long,
        payload: String,
        nextAttemptEpochMs: Long = 0,
    ) {
        dao.enqueueIfCurrent(
            OutboxEntity(
                eventId = eventId,
                sessionId = sessionId,
                revision = revision,
                payload = payload,
                nextAttemptEpochMs = nextAttemptEpochMs,
            ),
        )
    }

    suspend fun completeWithOutbox(record: SessionRecord, samples: List<HeartRateSample>, eventId: String, payload: String) {
        require(record.status == SessionStatus.COMPLETED) { "Only completed Wear sessions can enqueue completion" }
        dao.saveCompletedWithOutbox(
            record, samples,
            OutboxEntity(eventId = eventId, sessionId = record.id, revision = record.revision, payload = payload),
        )
    }

    suspend fun completeWithOutbox(record: SessionRecord, eventId: String, payload: String) =
        completeWithOutbox(record, record.heartRateSamples, eventId, payload)

    suspend fun checkpoint(record: SessionRecord, samples: List<HeartRateSample>) = dao.checkpoint(record, samples)

    suspend fun appendHeartRateBatch(record: SessionRecord, samples: List<HeartRateSample>) = checkpoint(record, samples)

    suspend fun pendingOutbox(): List<OutboxEntity> = dao.pendingOutbox()

    suspend fun acknowledge(eventId: String, revision: Long) {
        dao.deleteAcknowledged(eventId, revision)
    }
}

private fun SessionRecord.toWearEntity() = WearSessionEntity(
    id = id,
    revision = revision,
    durationMinutes = config.durationMinutes,
    intervalMinutes = config.intervalMinutes,
    status = status.name,
    startedAtEpochMillis = startEpochMillis,
    endedAtEpochMillis = endEpochMillis,
    activeDurationMs = activeDurationMs,
    pausedDurationMs = pausedDurationMs,
)

private fun HeartRateSample.toWearEntity(sessionId: String) = WearHeartRateEntity(
    sessionId = sessionId,
    epochMillis = epochMillis,
    bpm = bpm,
    accuracy = accuracy.name,
)

private fun WearSessionEntity.toRecord(samples: List<WearHeartRateEntity>) = SessionRecord(
    id = id,
    revision = revision,
    config = SessionConfig(durationMinutes, intervalMinutes),
    status = SessionStatus.valueOf(status),
    owner = SessionOwner.WEAR,
    startEpochMillis = startedAtEpochMillis,
    endEpochMillis = endedAtEpochMillis,
    activeDurationMs = activeDurationMs,
    pausedDurationMs = pausedDurationMs,
    heartRateSamples = samples.map { it.toSample() },
    healthConnectState = SyncState.LOCAL_ONLY,
)

private fun WearHeartRateEntity.toSample() = HeartRateSample(
    epochMillis = epochMillis,
    bpm = bpm,
    accuracy = SampleAccuracy.valueOf(accuracy),
)
