package app.zhanzhuang.timer.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMillis DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status IN ('STARTING', 'RUNNING', 'PAUSED', 'COMPLETING') ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun activeSession(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND healthConnectState = 'PENDING'")
    suspend fun pendingHealthSyncSessions(): List<SessionEntity>

    @Query("SELECT * FROM heart_rate_samples WHERE sessionId = :sessionId ORDER BY epochMillis")
    suspend fun samplesFor(sessionId: String): List<HeartRateEntity>

    @Query("SELECT * FROM mobile_runtime WHERE singletonId = 1")
    suspend fun runtimeSnapshot(): MobileRuntimeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflict(entity: SessionEntity): Long

    @Query("UPDATE sessions SET revision = :entityRevision, durationMinutes = :durationMinutes, intervalMinutes = :intervalMinutes, status = :status, owner = :owner, startedAtEpochMillis = :startedAtEpochMillis, endedAtEpochMillis = :endedAtEpochMillis, activeDurationMs = :activeDurationMs, pausedDurationMs = :pausedDurationMs, healthConnectState = :healthConnectState WHERE id = :id")
    suspend fun updateIfNewer(
        id: String,
        entityRevision: Long,
        durationMinutes: Int,
        intervalMinutes: Int,
        status: String,
        owner: String,
        startedAtEpochMillis: Long?,
        endedAtEpochMillis: Long?,
        activeDurationMs: Long,
        pausedDurationMs: Long,
        healthConnectState: String,
    ): Int

    @Query("DELETE FROM heart_rate_samples WHERE sessionId = :sessionId")
    suspend fun deleteSamples(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<HeartRateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRuntime(runtime: MobileRuntimeEntity)

    @Query("DELETE FROM mobile_runtime")
    suspend fun clearRuntime()

    @Transaction
    suspend fun upsertIfNewer(
        entity: SessionEntity,
        samples: List<HeartRateEntity>,
        runtime: MobileRuntimeEntity?,
    ) {
        val existing = get(entity.id)
        if (existing == null) {
            if (insertIgnoringConflict(entity) != -1L) {
                replaceSamples(entity.id, samples)
                replaceRuntime(runtime)
            }
            return
        }
        if (entity.revision <= existing.revision) return

        updateIfNewer(
            id = entity.id,
            entityRevision = entity.revision,
            durationMinutes = entity.durationMinutes,
            intervalMinutes = entity.intervalMinutes,
            status = entity.status,
            owner = entity.owner,
            startedAtEpochMillis = entity.startedAtEpochMillis,
            endedAtEpochMillis = entity.endedAtEpochMillis,
            activeDurationMs = entity.activeDurationMs,
            pausedDurationMs = entity.pausedDurationMs,
            healthConnectState = entity.healthConnectState,
        )
        replaceSamples(entity.id, samples)
        replaceRuntime(runtime)
    }

    private suspend fun replaceSamples(sessionId: String, samples: List<HeartRateEntity>) {
        deleteSamples(sessionId)
        if (samples.isNotEmpty()) insertSamples(samples)
    }

    private suspend fun replaceRuntime(runtime: MobileRuntimeEntity?) {
        if (runtime == null) clearRuntime() else saveRuntime(runtime)
    }
}
