package app.zhanzhuang.timer.mobile.ui

import app.zhanzhuang.timer.mobile.data.MobileRuntimeSnapshot
import app.zhanzhuang.timer.mobile.data.SessionRepository
import app.zhanzhuang.timer.mobile.health.HealthAvailability
import app.zhanzhuang.timer.mobile.health.HealthConnectGateway
import app.zhanzhuang.timer.mobile.health.HealthPermissionState
import app.zhanzhuang.timer.mobile.health.HealthWriteResult
import app.zhanzhuang.timer.mobile.session.MobileSessionUiState
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun healthPermissionRequestOnlyEmitsAfterExplicitSettingsAction() = runTest(dispatcher) {
        val permission = FakePermission()
        val model = model(permission = permission)
        advanceUntilIdle()

        val request = async { model.permissionRequests.first() }
        testScheduler.runCurrent()
        model.requestHealthPermissions()
        advanceUntilIdle()

        assertEquals(FakeGateway.permissions, request.await())
    }

    @Test
    fun serviceBridgeSuppliesTimerPresentationWithoutUiClock() = runTest(dispatcher) {
        val bridge = MutableStateFlow(MobileSessionUiState())
        val record = SessionRecord(status = SessionStatus.RUNNING, activeDurationMs = 60_000L)
        val model = model(bridge = bridge)
        advanceUntilIdle()

        bridge.value = MobileSessionUiState(record, activeElapsedMs = 75_000L, remainingMs = 1_725_000L)
        advanceUntilIdle()

        assertEquals(75_000L, model.state.value.training.activeElapsedMs)
        assertEquals(1_725_000L, model.state.value.training.remainingMs)
    }

    private fun model(
        bridge: StateFlow<MobileSessionUiState> = MutableStateFlow(MobileSessionUiState()),
        permission: FakePermission = FakePermission(),
    ) = MainViewModel(
        repository = FakeRepository(),
        serviceState = bridge,
        trainingActions = FakeActions(),
        defaults = FakeDefaults(),
        watchConnection = FakeWatchConnection(),
        healthGateway = FakeGateway(),
        healthPermission = permission,
        reconcileHealth = {},
        now = { Instant.parse("2026-08-01T12:00:00Z") },
        zoneId = { ZoneId.of("Europe/London") },
    )

    private class FakeRepository : SessionRepository {
        override fun observeSessions(): Flow<List<SessionRecord>> = flowOf(emptyList())
        override suspend fun get(id: String): SessionRecord? = null
        override suspend fun activeSession(): SessionRecord? = null
        override suspend fun runtimeSnapshot(): MobileRuntimeSnapshot? = null
        override suspend fun upsert(record: SessionRecord, runtimeSnapshot: MobileRuntimeSnapshot?) = Unit
    }

    private class FakeDefaults : TrainingDefaults {
        private val mutable = MutableStateFlow(SessionConfig())
        override val config: StateFlow<SessionConfig> = mutable.asStateFlow()
        override fun save(config: SessionConfig) { mutable.value = config }
    }

    private class FakeWatchConnection : WatchConnectionPort {
        override val states: Flow<WatchConnectionState> = flowOf(WatchConnectionState(false))
    }

    private class FakeActions : MobileTrainingActions {
        override suspend fun start(config: SessionConfig): SessionRecord = SessionRecord(config = config, status = SessionStatus.STARTING)
        override suspend fun pause(record: SessionRecord) = Unit
        override suspend fun resume(record: SessionRecord) = Unit
        override suspend fun finish(record: SessionRecord, cancelled: Boolean) = Unit
    }

    private class FakePermission : HealthPermissionPort {
        override suspend fun state(required: Set<String>): HealthPermissionState = HealthPermissionState.Missing(required)
    }

    private class FakeGateway : HealthConnectGateway {
        override suspend fun availability() = HealthAvailability.AVAILABLE
        override fun requiredWritePermissions(includeHeartRate: Boolean): Set<String> = permissions
        override suspend fun write(record: SessionRecord): HealthWriteResult = HealthWriteResult.Success(true, false)

        companion object { val permissions = setOf("write-session", "write-heart-rate") }
    }
}
