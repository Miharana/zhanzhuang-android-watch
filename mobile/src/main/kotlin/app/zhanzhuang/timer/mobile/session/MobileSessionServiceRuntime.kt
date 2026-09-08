package app.zhanzhuang.timer.mobile.session

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionStatus

interface MobileForegroundServiceHost {
    fun startTicker()
    fun stopTicker()
    fun removeForegroundNotification()
    fun stopService(startId: Int?): Boolean
}

class MobileSessionServiceRuntime(
    private val controller: MobileSessionController,
    private val host: MobileForegroundServiceHost,
) {
    suspend fun recover(tearDownWhenInactive: Boolean = true, startId: Int? = null): Boolean {
        val recovered = controller.recover()
        if (recovered || tearDownWhenInactive) synchronizeService(startId)
        return recovered
    }

    suspend fun start(config: SessionConfig, sessionId: String? = null, startId: Int? = null) {
        controller.start(config, sessionId)
        synchronizeService(startId)
    }

    suspend fun pause(startId: Int? = null) {
        controller.pause()
        synchronizeService(startId)
    }

    suspend fun resume(startId: Int? = null) {
        controller.resume()
        synchronizeService(startId)
    }

    suspend fun finish(cancelled: Boolean, startId: Int? = null) {
        controller.finish(cancelled)
        synchronizeService(startId)
    }

    suspend fun tick(startId: Int? = null) {
        controller.tick()
        synchronizeService(startId)
    }

    private fun synchronizeService(startId: Int?) {
        if (controller.state.value.session?.status in ACTIVE_STATUSES) {
            host.startTicker()
        } else {
            host.stopTicker()
            if (host.stopService(startId)) host.removeForegroundNotification()
        }
    }

    private companion object {
        val ACTIVE_STATUSES = setOf(
            SessionStatus.STARTING,
            SessionStatus.RUNNING,
            SessionStatus.PAUSED,
            SessionStatus.COMPLETING,
        )
    }
}
