package app.zhanzhuang.timer.mobile.sync

import app.zhanzhuang.timer.model.SessionRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ephemeral Wear FGS updates for UI presentation; Room remains the durable record source. */
object MobileWearRuntimeBridge {
    private val mutableRuntime = MutableStateFlow<SessionRuntime?>(null)
    val runtime: StateFlow<SessionRuntime?> = mutableRuntime.asStateFlow()

    fun publish(incoming: SessionRuntime) {
        val current = mutableRuntime.value
        if (
            current == null || current.sessionId != incoming.sessionId ||
            incoming.revision > current.revision ||
            (incoming.revision == current.revision && incoming.reportedAtEpochMillis >= current.reportedAtEpochMillis)
        ) {
            mutableRuntime.value = incoming
        }
    }
}
