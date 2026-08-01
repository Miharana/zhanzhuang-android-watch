package app.zhanzhuang.timer.mobile.session

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionStatus

interface MobileForegroundServiceHost {
    fun startTicker()
    fun stopTicker()
    fun removeForegroundNotification()
    fun stopSelf()
}

class MobileSessionServiceRuntime(
    private val controller: MobileSessionController,
    private val host: MobileForegroundServiceHost,
) {
    suspend fun recover(tearDownWhenInactive: Boolean = true): Boolean {
        val recovered = controller.recover()
        if (recovered || tearDownWhenInactive) synchronizeService()
        return recovered
    }

    suspend fun start(config: SessionConfig) {
        controller.start(config)
        synchronizeService()
    }

    suspend fun pause() {
        controller.pause()
        synchronizeService()
    }

    suspend fun resume() {
        controller.resume()
        synchronizeService()
    }

    suspend fun finish(cancelled: Boolean) {
        controller.finish(cancelled)
        synchronizeService()
    }

    suspend fun tick() {
        controller.tick()
        synchronizeService()
    }

    private fun synchronizeService() {
        if (controller.state.value.session?.status in ACTIVE_STATUSES) {
            host.startTicker()
        } else {
            host.stopTicker()
            host.removeForegroundNotification()
            host.stopSelf()
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
