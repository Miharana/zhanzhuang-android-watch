package app.zhanzhuang.timer.wear.ui

import app.zhanzhuang.timer.model.SessionConfig

/** UI-only setup and confirmation state. Timing remains exclusively in WearSessionController. */
data class WearSetupState(val config: SessionConfig = SessionConfig()) {
    fun withDurationDelta(steps: Int): WearSetupState = copy(
        config = config.copy(durationMinutes = (config.durationMinutes + steps * 5).coerceIn(15, 180).let { it - it % 5 }),
    )

    fun withIntervalDelta(steps: Int): WearSetupState {
        val values = listOf(5, 10, 15, 20, 30)
        val index = values.indexOf(config.intervalMinutes)
        return copy(config = config.copy(intervalMinutes = values[(index + steps).coerceIn(0, values.lastIndex)]))
    }

    fun withDuration(minutes: Int): WearSetupState = copy(config = config.copy(durationMinutes = minutes))
}

sealed interface WearUiState {
    data object Setup : WearUiState
    data object Active : WearUiState
    data object ConfirmFinish : WearUiState
    data object Permission : WearUiState
    data object Completion : WearUiState
}

sealed interface WearUiAction {
    data object RequestFinish : WearUiAction
    data object ConfirmFinish : WearUiAction
    data object DismissFinish : WearUiAction
}

sealed interface WearUiEffect {
    data object None : WearUiEffect
    data object Finish : WearUiEffect
}

object WearUiReducer {
    fun reduce(state: WearUiState, action: WearUiAction): WearUiState = when (action) {
        WearUiAction.RequestFinish -> if (state == WearUiState.Active) WearUiState.ConfirmFinish else state
        WearUiAction.ConfirmFinish -> if (state == WearUiState.ConfirmFinish) WearUiState.Active else state
        WearUiAction.DismissFinish -> if (state == WearUiState.ConfirmFinish) WearUiState.Active else state
    }

    fun effect(state: WearUiState, action: WearUiAction): WearUiEffect =
        if (state == WearUiState.ConfirmFinish && action == WearUiAction.ConfirmFinish) WearUiEffect.Finish else WearUiEffect.None
}

fun formatWearDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1_000L)
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
