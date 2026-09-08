package app.zhanzhuang.timer.mobile.ui

import app.zhanzhuang.timer.mobile.data.MobileRuntimeSnapshot
import app.zhanzhuang.timer.mobile.data.SessionRepository
import app.zhanzhuang.timer.mobile.health.HealthAvailability
import app.zhanzhuang.timer.mobile.health.HealthConnectGateway
import app.zhanzhuang.timer.mobile.health.HealthExportConsent
import app.zhanzhuang.timer.mobile.health.HealthPermissionState
import app.zhanzhuang.timer.mobile.health.HealthWriteResult
import app.zhanzhuang.timer.mobile.session.MobileSessionUiState
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRuntime
import app.zhanzhuang.timer.model.SessionStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
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
        val consent = FakeConsent()
        val model = model(permission = permission, consent = consent)
        advanceUntilIdle()

        val request = async { model.permissionRequests.first() }
        testScheduler.runCurrent()
        model.requestHealthPermissions()
        advanceUntilIdle()

        assertEquals(FakeGateway.permissions, request.await())
        assertTrue(consent.consentAccepted)
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

    @Test
    fun watchRuntimeRefreshesActiveProgressAndPauseFreezesTheServiceReportedValue() = runTest(dispatcher) {
        val runtime = MutableStateFlow<SessionRuntime?>(null)
        val record = SessionRecord(
            id = "watch-session",
            revision = 4,
            config = SessionConfig(),
            status = SessionStatus.RUNNING,
            owner = SessionOwner.WEAR,
            activeDurationMs = 60_000L,
        )
        val model = model(repository = FakeRepository(listOf(record)), runtime = runtime)
        advanceUntilIdle()

        runtime.value = SessionRuntime("watch-session", 4, SessionStatus.RUNNING, 120_000L, 1_680_000L, 10_000L)
        advanceUntilIdle()
        assertEquals(120_000L, model.state.value.training.activeElapsedMs)
        assertEquals(1_680_000L, model.state.value.training.remainingMs)

        runtime.value = SessionRuntime("watch-session", 4, SessionStatus.PAUSED, 120_000L, 1_680_000L, 15_000L)
        advanceUntilIdle()
        assertEquals(SessionStatus.PAUSED, model.state.value.training.session?.status)
        assertEquals(120_000L, model.state.value.training.activeElapsedMs)
    }

    @Test
    fun disconnectedStartPassesAnAuthoritativeOfflineRouteToTrainingActions() = runTest(dispatcher) {
        val actions = RecordingActions()
        val model = model(actions = actions)
        advanceUntilIdle()

        model.start()
        advanceUntilIdle()

        assertEquals(listOf(false), actions.watchReachability)
    }

    @Test
    fun startIgnoresDuplicateTapWhileTheFirstStartIsInFlight() = runTest(dispatcher) {
        val actions = BlockingStartActions()
        val model = model(actions = actions)
        advanceUntilIdle()

        model.start()
        testScheduler.runCurrent()
        model.start()
        testScheduler.runCurrent()

        assertTrue(model.state.value.training.actionInProgress)
        assertEquals(1, actions.calls)

        actions.release.complete(Unit)
        advanceUntilIdle()

        assertFalse(model.state.value.training.actionInProgress)
        assertEquals(1, actions.calls)
        assertEquals(SessionStatus.RUNNING, model.state.value.training.session?.status)
    }

    @Test
    fun failedStartIsReportedAndCanBeRetried() = runTest(dispatcher) {
        val actions = FailingStartActions()
        val model = model(actions = actions)
        advanceUntilIdle()

        model.start()
        advanceUntilIdle()

        assertFalse(model.state.value.training.actionInProgress)
        assertEquals(TrainingActionError.START_FAILED, model.state.value.training.actionError)
        assertEquals(1, actions.calls)
    }

    @Test
    fun reconnectRuntimeCatchesUpAndTerminalRepositoryStateSupersedesTheEphemeralCountdown() = runTest(dispatcher) {
        val runtime = MutableStateFlow<SessionRuntime?>(null)
        val repository = FakeRepository(listOf(watchRecord()))
        val watch = FakeWatchConnection(false)
        val model = model(repository = repository, runtime = runtime, watch = watch)
        advanceUntilIdle()
        runtime.value = SessionRuntime("watch-session", 4, SessionStatus.RUNNING, 120_000L, 1_680_000L, 10_000L)
        advanceUntilIdle()

        watch.publish(connected = true)
        runtime.value = SessionRuntime("watch-session", 4, SessionStatus.RUNNING, 180_000L, 1_620_000L, 20_000L)
        advanceUntilIdle()
        assertEquals(true, model.state.value.training.watchConnected)
        assertEquals(180_000L, model.state.value.training.activeElapsedMs)

        repository.publish(listOf(watchRecord().copy(revision = 5, status = SessionStatus.COMPLETED, activeDurationMs = 180_000L)))
        advanceUntilIdle()
        assertEquals(SessionStatus.COMPLETED, model.state.value.training.session?.status)
        assertEquals(180_000L, model.state.value.training.activeElapsedMs)
    }

    private fun model(
        bridge: StateFlow<MobileSessionUiState> = MutableStateFlow(MobileSessionUiState()),
        permission: FakePermission = FakePermission(),
        consent: FakeConsent = FakeConsent(),
        repository: FakeRepository = FakeRepository(),
        runtime: MutableStateFlow<SessionRuntime?> = MutableStateFlow(null),
        actions: MobileTrainingActions = FakeActions(),
        watch: FakeWatchConnection = FakeWatchConnection(false),
    ) = MainViewModel(
        repository = repository,
        serviceState = bridge,
        trainingActions = actions,
        defaults = FakeDefaults(),
        watchConnection = watch,
        wearRuntime = FakeWearRuntime(runtime),
        healthGateway = FakeGateway(),
        healthPermission = permission,
        healthExportConsent = consent,
        reconcileHealth = {},
        now = { Instant.parse("2026-08-01T12:00:00Z") },
        zoneId = { ZoneId.of("Europe/London") },
    )

    private class FakeRepository(records: List<SessionRecord> = emptyList()) : SessionRepository {
        private val mutableRecords = MutableStateFlow(records)
        override fun observeSessions(): Flow<List<SessionRecord>> = mutableRecords
        override suspend fun get(id: String): SessionRecord? = mutableRecords.value.firstOrNull { it.id == id }
        override suspend fun activeSession(): SessionRecord? = mutableRecords.value.firstOrNull { it.status in setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING) }
        override suspend fun runtimeSnapshot(): MobileRuntimeSnapshot? = null
        override suspend fun upsert(record: SessionRecord, runtimeSnapshot: MobileRuntimeSnapshot?) = Unit
        fun publish(records: List<SessionRecord>) { mutableRecords.value = records }
    }

    private class FakeDefaults : TrainingDefaults {
        private val mutable = MutableStateFlow(SessionConfig())
        override val config: StateFlow<SessionConfig> = mutable.asStateFlow()
        override fun save(config: SessionConfig) { mutable.value = config }
    }

    private class FakeWatchConnection(connected: Boolean) : WatchConnectionPort {
        private val mutable = MutableStateFlow(WatchConnectionState(connected))
        override val states: Flow<WatchConnectionState> = mutable
        fun publish(connected: Boolean) { mutable.value = WatchConnectionState(connected) }
    }

    private class FakeWearRuntime(private val runtime: MutableStateFlow<SessionRuntime?>) : WearRuntimePort {
        override val states: Flow<SessionRuntime?> = runtime
    }

    private class FakeActions : MobileTrainingActions {
        override suspend fun start(config: SessionConfig, watchReachable: Boolean): SessionRecord = SessionRecord(config = config, status = SessionStatus.STARTING)
        override suspend fun pause(record: SessionRecord) = Unit
        override suspend fun resume(record: SessionRecord) = Unit
        override suspend fun finish(record: SessionRecord, cancelled: Boolean) = Unit
    }

    private class RecordingActions : MobileTrainingActions {
        val watchReachability = mutableListOf<Boolean>()
        override suspend fun start(config: SessionConfig, watchReachable: Boolean): SessionRecord {
            watchReachability += watchReachable
            return SessionRecord(config = config, status = SessionStatus.RUNNING)
        }
        override suspend fun pause(record: SessionRecord) = Unit
        override suspend fun resume(record: SessionRecord) = Unit
        override suspend fun finish(record: SessionRecord, cancelled: Boolean) = Unit
    }

    private class BlockingStartActions : MobileTrainingActions {
        val release = CompletableDeferred<Unit>()
        var calls = 0

        override suspend fun start(config: SessionConfig, watchReachable: Boolean): SessionRecord {
            calls += 1
            release.await()
            return SessionRecord(config = config, status = SessionStatus.RUNNING)
        }

        override suspend fun pause(record: SessionRecord) = Unit
        override suspend fun resume(record: SessionRecord) = Unit
        override suspend fun finish(record: SessionRecord, cancelled: Boolean) = Unit
    }

    private class FailingStartActions : MobileTrainingActions {
        var calls = 0

        override suspend fun start(config: SessionConfig, watchReachable: Boolean): SessionRecord {
            calls += 1
            error("start failed")
        }

        override suspend fun pause(record: SessionRecord) = Unit
        override suspend fun resume(record: SessionRecord) = Unit
        override suspend fun finish(record: SessionRecord, cancelled: Boolean) = Unit
    }

    private class FakePermission : HealthPermissionPort {
        override suspend fun state(required: Set<String>): HealthPermissionState = HealthPermissionState.Missing(required)
    }

    private class FakeConsent(var consentAccepted: Boolean = false) : HealthExportConsent {
        override fun isAccepted(): Boolean = consentAccepted
        override fun accept() { consentAccepted = true }
    }

    private class FakeGateway : HealthConnectGateway {
        override suspend fun availability() = HealthAvailability.AVAILABLE
        override fun requiredWritePermissions(includeHeartRate: Boolean): Set<String> = permissions
        override suspend fun write(record: SessionRecord): HealthWriteResult = HealthWriteResult.Success(true, false)

        companion object { val permissions = setOf("write-session", "write-heart-rate") }
    }

    private fun watchRecord() = SessionRecord(
        id = "watch-session",
        revision = 4,
        config = SessionConfig(),
        status = SessionStatus.RUNNING,
        owner = SessionOwner.WEAR,
        activeDurationMs = 60_000L,
    )
}
