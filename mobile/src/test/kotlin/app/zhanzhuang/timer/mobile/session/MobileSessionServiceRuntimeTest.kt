package app.zhanzhuang.timer.mobile.session

import app.zhanzhuang.timer.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileSessionServiceRuntimeTest {
    @Test
    fun terminalTickTearsDownForegroundService() = runTest {
        val controller = TerminalController()
        val host = FakeServiceHost()
        val runtime = MobileSessionServiceRuntime(controller, host)

        runtime.tick()

        assertEquals(1, host.stopTickerCount)
        assertEquals(1, host.removeForegroundCount)
        assertEquals(1, host.stopSelfCount)
    }

    private class TerminalController : MobileSessionController {
        private val mutableState = MutableStateFlow(MobileSessionUiState())
        override val state: StateFlow<MobileSessionUiState> = mutableState
        override suspend fun recover(): Boolean = false
        override suspend fun start(config: app.zhanzhuang.timer.model.SessionConfig, sessionId: String?) = Unit
        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        override suspend fun finish(cancelled: Boolean) = Unit
        override suspend fun tick() {
            mutableState.value = MobileSessionUiState(
                session = app.zhanzhuang.timer.model.SessionRecord(status = SessionStatus.COMPLETED),
            )
        }
    }

    private class FakeServiceHost : MobileForegroundServiceHost {
        var stopTickerCount = 0
        var removeForegroundCount = 0
        var stopSelfCount = 0
        override fun startTicker() = Unit
        override fun stopTicker() {
            stopTickerCount += 1
        }
        override fun removeForegroundNotification() {
            removeForegroundCount += 1
        }
        override fun stopService(startId: Int?): Boolean {
            stopSelfCount += 1
            return true
        }
    }
}
