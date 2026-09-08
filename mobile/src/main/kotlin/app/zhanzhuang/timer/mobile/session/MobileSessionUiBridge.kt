package app.zhanzhuang.timer.mobile.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local observation point for the foreground service; the controller remains authoritative. */
object MobileSessionUiBridge {
    private val mutableState = MutableStateFlow(MobileSessionUiState())
    val state: StateFlow<MobileSessionUiState> = mutableState.asStateFlow()

    internal fun publish(state: MobileSessionUiState) {
        mutableState.value = state
    }
}
