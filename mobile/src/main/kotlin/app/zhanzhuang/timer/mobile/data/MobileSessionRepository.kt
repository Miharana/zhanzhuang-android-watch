package app.zhanzhuang.timer.mobile.data

import app.zhanzhuang.timer.model.SessionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SessionRepository {
    fun observeSessions(): Flow<List<SessionRecord>>
    suspend fun get(id: String): SessionRecord?
    suspend fun activeSession(): SessionRecord?
    suspend fun upsert(record: SessionRecord)
}

class MobileSessionRepository(database: MobileDatabase) : SessionRepository {
    private val dao = database.sessionDao()

    override fun observeSessions(): Flow<List<SessionRecord>> = dao.observeAll().map { sessions ->
        sessions.map { session -> session.toRecord(dao.samplesFor(session.id)) }
    }

    override suspend fun get(id: String): SessionRecord? = dao.get(id)?.let { session ->
        session.toRecord(dao.samplesFor(session.id))
    }

    override suspend fun activeSession(): SessionRecord? = dao.activeSession()?.let { session ->
        session.toRecord(dao.samplesFor(session.id))
    }

    override suspend fun upsert(record: SessionRecord) {
        dao.upsertIfNewer(record.toEntity(), record.heartRateSamples.map { it.toEntity(record.id) })
    }
}
