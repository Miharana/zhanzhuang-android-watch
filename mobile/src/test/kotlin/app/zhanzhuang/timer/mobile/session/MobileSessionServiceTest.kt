package app.zhanzhuang.timer.mobile.session

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileSessionServiceTest {
    @AfterTest
    fun clearDependencies() {
        MobileSessionService.testDependencies = null
    }

    @Test
    fun recreatedServiceRecoversBeforePauseAction() = runTest {
        val controller = RecordingController()
        val service = serviceWith(controller)

        dispatch(service, MobileSessionService.ACTION_PAUSE)

        assertEquals(listOf("recover", "pause"), controller.calls)
    }

    @Test
    fun recreatedServiceRecoversBeforeFinishAction() = runTest {
        val controller = RecordingController(finishIsTerminal = true)
        val service = serviceWith(controller)

        dispatch(service, MobileSessionService.ACTION_FINISH)

        assertEquals(listOf("recover", "finish"), controller.calls)
    }

    @Test
    fun recreatedServiceDoesNotStartOverRecoveredActiveSession() = runTest {
        val controller = RecordingController()
        val service = serviceWith(controller)

        dispatch(service, MobileSessionService.ACTION_START)

        assertEquals(listOf("recover"), controller.calls)
    }

    @Test
    fun recreatedServiceRecoversBeforeResumeAction() = runTest {
        val controller = RecordingController()
        val service = serviceWith(controller)

        dispatch(service, MobileSessionService.ACTION_RESUME)

        assertEquals(listOf("recover", "resume"), controller.calls)
    }

    private suspend fun dispatch(service: MobileSessionService, action: String) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MobileSessionService::class.java)
            .setAction(action)
        service.onStartCommand(intent, 0, 1)
        service.lastActionJob!!.join()
    }

    private fun serviceWith(controller: RecordingController): MobileSessionService {
        MobileSessionService.testDependencies = MobileSessionServiceDependencies(
            controllerFactory = { controller },
            hostFactory = { NoopHost() },
            dispatcher = Dispatchers.Default,
        )
        return Robolectric.buildService(MobileSessionService::class.java).create().get()
    }

    private class RecordingController(
        private val finishIsTerminal: Boolean = false,
    ) : MobileSessionController {
        private val mutableState = MutableStateFlow(
            MobileSessionUiState(session = SessionRecord(status = SessionStatus.RUNNING)),
        )
        override val state: StateFlow<MobileSessionUiState> = mutableState
        val calls = mutableListOf<String>()

        override suspend fun recover(): Boolean {
            calls += "recover"
            return true
        }

        override suspend fun start(config: SessionConfig) {
            calls += "start"
        }

        override suspend fun pause() {
            calls += "pause"
        }

        override suspend fun resume() {
            calls += "resume"
        }

        override suspend fun finish(cancelled: Boolean) {
            calls += "finish"
            if (finishIsTerminal) {
                mutableState.value = MobileSessionUiState(
                    session = SessionRecord(status = SessionStatus.COMPLETED),
                )
            }
        }

        override suspend fun tick() = Unit
    }

    private class NoopHost : MobileForegroundServiceHost {
        override fun startTicker() = Unit
        override fun stopTicker() = Unit
        override fun removeForegroundNotification() = Unit
        override fun stopSelf() = Unit
    }
}
