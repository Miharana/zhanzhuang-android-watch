package app.zhanzhuang.timer.mobile.sync

import android.content.Context
import app.zhanzhuang.timer.domain.SyncConflictResolver
import app.zhanzhuang.timer.mobile.data.SessionRepository
import app.zhanzhuang.timer.mobile.health.HealthSyncWorker
import app.zhanzhuang.timer.mobile.session.MobileSessionService
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus

/** Adapter that lets synchronization use the Task 2 repository without owning its runtime service. */
class MobileRepositorySyncController(
    private val repository: SessionRepository,
    private val nowEpochMillis: () -> Long,
    private val appContext: Context? = null,
    private val resolver: SyncConflictResolver = SyncConflictResolver(),
) : MobileSyncController {
    override suspend fun startFromMobile(config: SessionConfig, sessionId: String, revision: Long): SessionRecord =
        SessionRecord(
            id = sessionId,
            revision = revision,
            config = config,
            status = SessionStatus.STARTING,
            owner = SessionOwner.MOBILE,
            startEpochMillis = nowEpochMillis(),
        ).also { repository.upsert(it) }

    override suspend fun startFromRemote(config: SessionConfig, sessionId: String, revision: Long): SessionRecord {
        repository.get(sessionId)?.let { existing ->
            if (existing.status in TERMINAL_STATUSES || (existing.owner == SessionOwner.WEAR && existing.status in ACTIVE_STATUSES)) {
                return existing
            }
        }
        return mergeRemote(
            SessionRecord(
            id = sessionId,
            revision = revision + 1,
            config = config,
            status = SessionStatus.RUNNING,
            owner = SessionOwner.WEAR,
            startEpochMillis = nowEpochMillis(),
            ),
        )
    }

    override suspend fun current(): SessionRecord? = repository.activeSession()

    override suspend fun session(sessionId: String): SessionRecord? = repository.get(sessionId)

    override suspend fun pauseFromRemote(sessionId: String): SessionRecord? = transitionActive(sessionId, SessionStatus.PAUSED)

    override suspend fun resumeFromRemote(sessionId: String): SessionRecord? = transitionActive(sessionId, SessionStatus.RUNNING)

    override suspend fun finishFromRemote(sessionId: String, cancelled: Boolean): SessionRecord? = transitionActive(sessionId,
        if (cancelled) SessionStatus.CANCELLED else SessionStatus.COMPLETED,
    )

    override suspend fun becomeMobileOwner(sessionId: String): SessionRecord? {
        val current = repository.get(sessionId) ?: return null
        return current.copy(
            owner = SessionOwner.MOBILE,
            status = SessionStatus.RUNNING,
            revision = current.revision + 1,
        ).also {
            repository.upsert(it)
            appContext?.let { context -> MobileSessionService.start(context, it.config, it.id) }
        }
    }

    override suspend fun mergeRemote(record: SessionRecord): SessionRecord {
        val current = repository.get(record.id)
        val winner = resolver.merge(current, record)
        if (winner == record) {
            val accepted = if (
                current?.revision == record.revision &&
                current.status == SessionStatus.STARTING &&
                current.owner == SessionOwner.MOBILE &&
                record.owner == SessionOwner.WEAR
            ) {
                record.copy(revision = record.revision + 1)
            } else record
            val durable = if (accepted.status == SessionStatus.COMPLETED && accepted.activeDurationMs >= HealthSyncWorker.MIN_EXPORT_DURATION_MILLIS) {
                accepted.copy(healthConnectState = app.zhanzhuang.timer.model.SyncState.PENDING)
            } else {
                accepted
            }
            repository.upsert(durable)
            val stored = repository.get(durable.id)
            check(stored != null && stored.revision >= durable.revision) { "Sync record was not durably persisted" }
            if (durable.status == SessionStatus.COMPLETED && durable.activeDurationMs >= HealthSyncWorker.MIN_EXPORT_DURATION_MILLIS) {
                val context = requireNotNull(appContext) { "Completed sync requires an application context" }
                check(HealthSyncWorker.enqueue(context, durable)) { "Health sync was not scheduled" }
            }
            return durable
        }
        return winner
    }

    private suspend fun transitionActive(sessionId: String, status: SessionStatus): SessionRecord? {
        val active = repository.get(sessionId) ?: return null
        if (active.status == status) return active
        if (active.status in TERMINAL_STATUSES || active.status !in ACTIVE_STATUSES) return null
        return active.copy(
            status = status,
            revision = active.revision + 1,
            endEpochMillis = if (status == SessionStatus.COMPLETED || status == SessionStatus.CANCELLED) nowEpochMillis() else null,
        ).also {
            repository.upsert(it)
            appContext?.let { context ->
                val action = when (status) {
                    SessionStatus.PAUSED -> MobileSessionService.ACTION_PAUSE
                    SessionStatus.RUNNING -> MobileSessionService.ACTION_RESUME
                    else -> MobileSessionService.ACTION_FINISH
                }
                context.startService(
                    android.content.Intent(context, MobileSessionService::class.java)
                        .setAction(action)
                        .putExtra(MobileSessionService.EXTRA_CANCELLED, status == SessionStatus.CANCELLED),
                )
            }
        }
    }

    private companion object {
        val ACTIVE_STATUSES = setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
        val TERMINAL_STATUSES = setOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.INTERRUPTED)
    }
}
