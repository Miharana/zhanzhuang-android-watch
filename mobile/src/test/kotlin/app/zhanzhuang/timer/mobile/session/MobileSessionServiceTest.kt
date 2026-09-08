package app.zhanzhuang.timer.mobile.session

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun explicitCancelledFinishReachesTheRuntimeAsCancellation() = runTest {
        val controller = RecordingController(finishIsTerminal = true)
        val service = serviceWith(controller)

        service.onStartCommand(
            intent(MobileSessionService.ACTION_FINISH)
                .putExtra(MobileSessionService.EXTRA_CANCELLED, true),
            0,
            1,
        )
        service.lastActionJob!!.join()

        assertEquals(listOf(true), controller.finishCancelled)
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

    @Test
    fun newerStartSurvivesBlockedTerminalFinish() = runTest {
        val controller = BlockingFinishController()
        MobileSessionService.testDependencies = MobileSessionServiceDependencies(
            controllerFactory = { controller },
            dispatcher = Dispatchers.Default,
        )
        val service = Robolectric.buildService(StartAwareMobileSessionService::class.java).create().get()

        service.onStartCommand(intent(MobileSessionService.ACTION_FINISH), 0, 1)
        val finishJob = service.lastActionJob!!
        controller.finishEntered.await()
        service.onStartCommand(intent(MobileSessionService.ACTION_START), 0, 2)
        val startJob = service.lastActionJob!!
        controller.allowFinish.complete(Unit)
        finishJob.join()
        startJob.join()

        assertEquals(listOf("recover", "finish", "recover", "start"), controller.calls)
        assertTrue(controller.started)
        assertTrue(service.stopSelfResultStartIds.isNotEmpty())
        assertTrue(service.stopSelfResultStartIds.all { it == 1 })
        assertFalse(2 in service.stopSelfResultStartIds)
    }

    private suspend fun dispatch(service: MobileSessionService, action: String) {
        val intent = intent(action)
        service.onStartCommand(intent, 0, 1)
        service.lastActionJob!!.join()
    }

    private fun intent(action: String) = Intent(
        ApplicationProvider.getApplicationContext(),
        MobileSessionService::class.java,
    ).setAction(action)

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
        val finishCancelled = mutableListOf<Boolean>()

        override suspend fun recover(): Boolean {
            calls += "recover"
            return true
        }

        override suspend fun start(config: SessionConfig, sessionId: String?) {
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
            finishCancelled += cancelled
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
        override fun stopService(startId: Int?): Boolean = true
    }

    private class BlockingFinishController : MobileSessionController {
        private val mutableState = MutableStateFlow(
            MobileSessionUiState(session = SessionRecord(status = SessionStatus.RUNNING)),
        )
        override val state: StateFlow<MobileSessionUiState> = mutableState
        val calls = mutableListOf<String>()
        val finishEntered = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        var started = false

        override suspend fun recover(): Boolean {
            calls += "recover"
            return state.value.session?.status == SessionStatus.RUNNING
        }

        override suspend fun start(config: SessionConfig, sessionId: String?) {
            calls += "start"
            started = true
            mutableState.value = MobileSessionUiState(
                session = SessionRecord(status = SessionStatus.RUNNING),
            )
        }

        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        override suspend fun finish(cancelled: Boolean) {
            calls += "finish"
            finishEntered.complete(Unit)
            allowFinish.await()
            mutableState.value = MobileSessionUiState(
                session = SessionRecord(status = SessionStatus.COMPLETED),
            )
        }
        override suspend fun tick() = Unit
    }

    private class StartAwareMobileSessionService : MobileSessionService() {
        val stopSelfResultStartIds = mutableListOf<Int>()

        override fun requestStop(startId: Int): Boolean {
            stopSelfResultStartIds += startId
            return false
        }
    }
}
