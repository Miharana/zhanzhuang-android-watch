package app.zhanzhuang.timer.mobile.sync

import app.zhanzhuang.timer.domain.SyncConflictResolver
import app.zhanzhuang.timer.mobile.data.SessionRepository
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus

/** Adapter that lets synchronization use the Task 2 repository without owning its runtime service. */
class MobileRepositorySyncController(
    private val repository: SessionRepository,
    private val nowEpochMillis: () -> Long,
    private val resolver: SyncConflictResolver = SyncConflictResolver(),
) : MobileSyncController {
    override suspend fun startFromMobile(config: SessionConfig, sessionId: String, revision: Long): SessionRecord =
        SessionRecord(
            id = sessionId,
            revision = revision,
            config = config,
            status = SessionStatus.RUNNING,
            owner = SessionOwner.MOBILE,
            startEpochMillis = nowEpochMillis(),
        ).also { repository.upsert(it) }

    override suspend fun startFromRemote(config: SessionConfig, sessionId: String, revision: Long): SessionRecord =
        mergeRemote(
            SessionRecord(
            id = sessionId,
            revision = revision,
            config = config,
            status = SessionStatus.RUNNING,
            owner = SessionOwner.WEAR,
            startEpochMillis = nowEpochMillis(),
            ),
        )

    override suspend fun current(): SessionRecord? = repository.activeSession()

    override suspend fun session(sessionId: String): SessionRecord? = repository.get(sessionId)

    override suspend fun pauseFromRemote(): SessionRecord? = transitionActive(SessionStatus.PAUSED)

    override suspend fun resumeFromRemote(): SessionRecord? = transitionActive(SessionStatus.RUNNING)

    override suspend fun finishFromRemote(cancelled: Boolean): SessionRecord? = transitionActive(
        if (cancelled) SessionStatus.CANCELLED else SessionStatus.COMPLETED,
    )

    override suspend fun becomeMobileOwner(sessionId: String): SessionRecord? {
        val current = repository.get(sessionId) ?: return null
        return current.copy(owner = SessionOwner.MOBILE, revision = current.revision + 1).also { repository.upsert(it) }
    }

    override suspend fun mergeRemote(record: SessionRecord): SessionRecord {
        val winner = resolver.merge(repository.get(record.id), record)
        if (winner == record) repository.upsert(record)
        return winner
    }

    private suspend fun transitionActive(status: SessionStatus): SessionRecord? {
        val active = repository.activeSession() ?: return null
        return active.copy(
            status = status,
            revision = active.revision + 1,
            endEpochMillis = if (status == SessionStatus.COMPLETED || status == SessionStatus.CANCELLED) nowEpochMillis() else null,
        ).also { repository.upsert(it) }
    }
}
