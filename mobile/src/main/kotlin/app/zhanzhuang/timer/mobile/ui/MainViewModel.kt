package app.zhanzhuang.timer.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zhanzhuang.timer.mobile.data.SessionRepository
import app.zhanzhuang.timer.mobile.health.HealthAvailability
import app.zhanzhuang.timer.mobile.health.HealthConnectGateway
import app.zhanzhuang.timer.mobile.health.HealthExportConsent
import app.zhanzhuang.timer.mobile.health.HealthPermissionState
import app.zhanzhuang.timer.mobile.session.MobileSessionUiState
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SessionRuntime
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class WatchConnectionState(val connected: Boolean, val name: String? = null)

interface WatchConnectionPort {
    val states: Flow<WatchConnectionState>
}

interface WearRuntimePort {
    val states: Flow<SessionRuntime?>
}

interface HealthPermissionPort {
    suspend fun state(required: Set<String>): HealthPermissionState
}

interface TrainingDefaults {
    val config: StateFlow<SessionConfig>
    fun save(config: SessionConfig)
}

interface MobileTrainingActions {
    suspend fun start(config: SessionConfig, watchReachable: Boolean): SessionRecord
    suspend fun pause(record: SessionRecord)
    suspend fun resume(record: SessionRecord)
    suspend fun finish(record: SessionRecord, cancelled: Boolean)
}

enum class TrainingActionError {
    START_FAILED,
    UPDATE_FAILED,
}

data class TrainingUiState(
    val config: SessionConfig = SessionConfig(),
    val session: SessionRecord? = null,
    val activeElapsedMs: Long = 0,
    val remainingMs: Long = 0,
    val watchConnected: Boolean = false,
    val watchName: String? = null,
    val actionInProgress: Boolean = false,
    val actionError: TrainingActionError? = null,
)

data class HealthUiState(
    val availability: HealthAvailability? = null,
    val permission: HealthPermissionState = HealthPermissionState.Missing(emptySet()),
    val exportConsentAccepted: Boolean = false,
)

enum class MobileDestination { TRAINING, HISTORY, SETTINGS, DETAIL }

data class MobileAppUiState(
    val destination: MobileDestination = MobileDestination.TRAINING,
    val training: TrainingUiState = TrainingUiState(),
    val history: List<SessionRecord> = emptyList(),
    val historyStats: HistoryStats = HistoryStats(0, 0, 0),
    val selectedRecord: SessionRecord? = null,
    val health: HealthUiState = HealthUiState(),
)

/**
 * Android-specific callers are represented by ports, keeping UI state and command routing unit-testable.
 * The UI never advances a timer: active elapsed time is supplied by the foreground service bridge.
 */
class MainViewModel(
    private val repository: SessionRepository,
    private val serviceState: StateFlow<MobileSessionUiState>,
    private val trainingActions: MobileTrainingActions,
    private val defaults: TrainingDefaults,
    private val watchConnection: WatchConnectionPort,
    private val wearRuntime: WearRuntimePort,
    private val healthGateway: HealthConnectGateway,
    private val healthPermission: HealthPermissionPort,
    private val healthExportConsent: HealthExportConsent,
    private val reconcileHealth: suspend () -> Unit,
    private val now: () -> Instant = Instant::now,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MobileAppUiState())
    val state: StateFlow<MobileAppUiState> = mutableState.asStateFlow()
    private val mutablePermissionRequests = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)
    val permissionRequests: SharedFlow<Set<String>> = mutablePermissionRequests.asSharedFlow()

    private var records: List<SessionRecord> = emptyList()
    private var controllerState = MobileSessionUiState()
    private var connection = WatchConnectionState(connected = false)
    private var remoteRuntime: SessionRuntime? = null

    init {
        viewModelScope.launch {
            defaults.config.collect { config -> update { copy(training = training.copy(config = config)) } }
        }
        viewModelScope.launch {
            repository.observeSessions().collect { observed ->
                records = observed.sortedByDescending { it.startEpochMillis ?: Long.MIN_VALUE }
                refreshTrainingFromSources()
                update {
                    copy(
                        history = records,
                        historyStats = HistoryStats.calculate(records, now(), zoneId()),
                    )
                }
            }
        }
        viewModelScope.launch {
            serviceState.collect {
                controllerState = it
                refreshTrainingFromSources()
            }
        }
        viewModelScope.launch {
            watchConnection.states.collect {
                connection = it
                refreshTrainingFromSources()
            }
        }
        viewModelScope.launch {
            wearRuntime.states.collect {
                remoteRuntime = it
                refreshTrainingFromSources()
            }
        }
        refreshHealthStatus()
    }

    fun selectDuration(minutes: Int) = updateConfig { it.copy(durationMinutes = minutes) }

    fun stepDuration(step: Int) = updateConfig {
        it.copy(durationMinutes = (it.durationMinutes + step * 5).coerceIn(15, 180).let { value -> value - value % 5 })
    }

    fun selectInterval(minutes: Int) = updateConfig { it.copy(intervalMinutes = minutes) }

    fun start() {
        launchTrainingAction(TrainingActionError.START_FAILED) {
            val current = state.value.training
            if (current.session?.status in ACTIVE_STATUSES) return@launchTrainingAction
            val record = trainingActions.start(current.config, current.watchConnected)
            update {
                copy(
                    training = training.copy(
                        session = record,
                        activeElapsedMs = record.activeDurationMs,
                        remainingMs = durationMs(record) - record.activeDurationMs,
                    ),
                )
            }
        }
    }

    fun pause() = withActiveRecord { trainingActions.pause(it) }
    fun resume() = withActiveRecord { trainingActions.resume(it) }
    fun finish(cancelled: Boolean) = withActiveRecord { trainingActions.finish(it, cancelled) }

    /** Called only after the in-app Health Connect disclosure has been accepted. */
    fun requestHealthPermissions() = viewModelScope.launch {
        val availability = healthGateway.availability()
        if (availability == HealthAvailability.UNAVAILABLE) {
            update { copy(health = HealthUiState(availability, health.permission, healthExportConsent.isAccepted())) }
            return@launch
        }
        healthExportConsent.accept()
        update { copy(health = health.copy(exportConsentAccepted = true)) }
        val required = healthGateway.requiredWritePermissions(includeHeartRate = true)
        when (val permissionState = healthPermission.state(required)) {
            HealthPermissionState.Granted -> update { copy(health = health.copy(permission = permissionState)) }
            is HealthPermissionState.Missing -> {
                update { copy(health = health.copy(permission = permissionState)) }
                mutablePermissionRequests.emit(permissionState.permissions)
            }
        }
    }

    fun onHealthPermissionResult() = viewModelScope.launch {
        refreshHealthStatusNow()
        if (healthExportConsent.isAccepted() && state.value.health.permission is HealthPermissionState.Granted) {
            reconcileHealth()
        }
    }

    fun retryHealthSync() = viewModelScope.launch { reconcileHealth(); refreshHealthStatus() }

    fun open(destination: MobileDestination) = update { copy(destination = destination) }
    fun showDetail(record: SessionRecord) = update { copy(destination = MobileDestination.DETAIL, selectedRecord = record) }
    fun backToHistory() = update { copy(destination = MobileDestination.HISTORY, selectedRecord = null) }

    private fun refreshHealthStatus() = viewModelScope.launch { refreshHealthStatusNow() }

    private suspend fun refreshHealthStatusNow() {
        val availability = healthGateway.availability()
        val required = healthGateway.requiredWritePermissions(includeHeartRate = true)
        val permission = healthPermission.state(required)
        update { copy(health = HealthUiState(availability, permission, healthExportConsent.isAccepted())) }
    }

    private fun updateConfig(change: (SessionConfig) -> SessionConfig) {
        val config = change(state.value.training.config)
        defaults.save(config)
    }

    private fun withActiveRecord(action: suspend (SessionRecord) -> Unit) {
        launchTrainingAction(TrainingActionError.UPDATE_FAILED) {
            state.value.training.session?.takeIf { it.status in ACTIVE_STATUSES }?.let { action(it) }
        }
    }

    private fun launchTrainingAction(
        failure: TrainingActionError,
        action: suspend () -> Unit,
    ) = viewModelScope.launch {
        if (state.value.training.actionInProgress) return@launch
        update { copy(training = training.copy(actionInProgress = true, actionError = null)) }
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            update { copy(training = training.copy(actionError = failure)) }
        } finally {
            update { copy(training = training.copy(actionInProgress = false)) }
        }
    }

    private fun refreshTrainingFromSources() {
        val repositoryActive = records.firstOrNull { it.status in ACTIVE_STATUSES }
        val repositoryTerminal = records.firstOrNull { it.status in TERMINAL_STATUSES }
        val bridged = controllerState.session
        val durable = when {
            repositoryActive != null && (bridged == null || bridged.id != repositoryActive.id || bridged.status !in ACTIVE_STATUSES) -> repositoryActive
            bridged != null -> bridged
            else -> repositoryTerminal
        }
        val runtime = remoteRuntime?.takeIf {
            durable?.owner == SessionOwner.WEAR &&
                it.sessionId == durable.id &&
                it.revision >= durable.revision &&
                it.status in ACTIVE_STATUSES
        }
        val authoritative = runtime?.let { durable?.copy(status = it.status, activeDurationMs = it.activeDurationMs) } ?: durable
        val active = when {
            bridged?.id == authoritative?.id -> controllerState.activeElapsedMs
            runtime != null -> runtime.activeDurationMs
            else -> authoritative?.activeDurationMs ?: 0
        }
        val remaining = when {
            bridged?.id == authoritative?.id -> controllerState.remainingMs
            runtime != null -> runtime.remainingMs
            else -> authoritative?.let(::durationMs)?.minus(active)?.coerceAtLeast(0) ?: 0
        }
        update {
            copy(training = training.copy(
                session = authoritative,
                activeElapsedMs = active,
                remainingMs = remaining,
                watchConnected = connection.connected,
                watchName = connection.name,
            ))
        }
    }

    private fun durationMs(record: SessionRecord) = record.config.durationMinutes * 60_000L
    private fun update(transform: MobileAppUiState.() -> MobileAppUiState) { mutableState.value = mutableState.value.transform() }

    private companion object {
        val ACTIVE_STATUSES = setOf(SessionStatus.STARTING, SessionStatus.RUNNING, SessionStatus.PAUSED, SessionStatus.COMPLETING)
        val TERMINAL_STATUSES = setOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.INTERRUPTED)
    }
}
