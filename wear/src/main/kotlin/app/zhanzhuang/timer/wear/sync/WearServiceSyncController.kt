package app.zhanzhuang.timer.wear.sync

import android.content.Context
import android.content.Intent
import android.os.Build
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.data.WearSessionRepository
import app.zhanzhuang.timer.wear.session.WearSessionService

/** Routes remote commands through the existing health foreground service. */
class WearServiceSyncController(
    private val context: Context,
    private val repository: WearSessionRepository,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : WearSyncController {
    private var activeSessionId: String? = null

    override suspend fun startFromRemote(config: SessionConfig, sessionId: String, revision: Long): SessionRecord {
        val existing = repository.get(sessionId)
        if (existing != null) return existing
        activeSessionId = sessionId
        startForeground(WearSessionService.startIntent(context, config, sessionId))
        // The service immediately persists STARTING/RUNNING; this state also
        // gives the phone a strictly newer ownership acknowledgement.
        return SessionRecord(
            id = sessionId,
            revision = revision + 1,
            config = config,
            status = SessionStatus.STARTING,
            owner = SessionOwner.WEAR,
            startEpochMillis = nowEpochMillis(),
        )
    }

    override suspend fun current(): SessionRecord? {
        val sessionId = activeSessionId ?: return null
        return repository.get(sessionId)
    }

    override suspend fun session(sessionId: String): SessionRecord? = repository.get(sessionId)

    override suspend fun pauseFromRemote(): SessionRecord? = command(WearSessionService.ACTION_PAUSE)

    override suspend fun resumeFromRemote(): SessionRecord? = command(WearSessionService.ACTION_RESUME)

    override suspend fun finishFromRemote(cancelled: Boolean): SessionRecord? = command(
        if (cancelled) WearSessionService.ACTION_CANCEL else WearSessionService.ACTION_FINISH,
    )

    override suspend fun mergeRemote(record: SessionRecord): SessionRecord = repository.get(record.id) ?: record

    private suspend fun command(action: String): SessionRecord? {
        val current = current() ?: return null
        startForeground(Intent(context, WearSessionService::class.java).setAction(action))
        return current
    }

    private fun startForeground(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }
}
