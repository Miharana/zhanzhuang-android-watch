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
) : WearSyncController {
    override suspend fun startFromRemote(config: SessionConfig, sessionId: String, revision: Long): SessionRecord? {
        // A matching ID is a durable idempotency key. In particular, never let
        // the service's TERMINAL + Start recovery path recreate it.
        repository.get(sessionId)?.let { return it }
        val active = repository.activeSession()
        if (active != null && active.id != sessionId) return null
        return WearServiceCommandGateway.dispatch { requestId ->
            startForeground(WearSessionService.startIntent(context, config, sessionId)
                .putExtra(WearSessionService.EXTRA_REQUEST_ID, requestId)
                .putExtra(WearSessionService.EXTRA_SESSION_ID, sessionId))
        }?.takeIf { it.id == sessionId && it.owner == SessionOwner.WEAR }
    }

    override suspend fun current(): SessionRecord? = repository.activeSession()

    override suspend fun session(sessionId: String): SessionRecord? = repository.get(sessionId)

    override suspend fun pauseFromRemote(sessionId: String): SessionRecord? = command(sessionId, WearSessionService.ACTION_PAUSE)

    override suspend fun resumeFromRemote(sessionId: String): SessionRecord? = command(sessionId, WearSessionService.ACTION_RESUME)

    override suspend fun finishFromRemote(sessionId: String, cancelled: Boolean): SessionRecord? = command(sessionId,
        if (cancelled) WearSessionService.ACTION_CANCEL else WearSessionService.ACTION_FINISH,
    )

    override suspend fun mergeRemote(record: SessionRecord): SessionRecord = repository.get(record.id) ?: record

    private suspend fun command(sessionId: String, action: String): SessionRecord? {
        val current = repository.get(sessionId) ?: return null
        when (action) {
            WearSessionService.ACTION_PAUSE -> if (current.status == SessionStatus.PAUSED) return current
            WearSessionService.ACTION_RESUME -> if (current.status == SessionStatus.RUNNING) return current
            WearSessionService.ACTION_FINISH,
            WearSessionService.ACTION_CANCEL -> if (current.status in TERMINAL_STATUSES) return current
        }
        if (current.status !in ACTIVE_STATUSES) return null
        return WearServiceCommandGateway.dispatch { requestId ->
            startForeground(Intent(context, WearSessionService::class.java)
                .setAction(action)
                .putExtra(WearSessionService.EXTRA_SESSION_ID, sessionId)
                .putExtra(WearSessionService.EXTRA_REQUEST_ID, requestId))
        }?.takeIf { it.id == sessionId && it.revision > current.revision }
    }

    private fun startForeground(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    private companion object {
        val ACTIVE_STATUSES = setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
        val TERMINAL_STATUSES = setOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.INTERRUPTED)
    }
}
