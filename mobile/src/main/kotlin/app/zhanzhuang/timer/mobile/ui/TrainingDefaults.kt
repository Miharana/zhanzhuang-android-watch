package app.zhanzhuang.timer.mobile.ui

import android.content.Context
import androidx.core.content.edit
import app.zhanzhuang.timer.model.SessionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small, local-only defaults storage; no account or network data is involved. */
class SharedPreferencesTrainingDefaults(context: Context) : TrainingDefaults {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutableConfig = MutableStateFlow(read())
    override val config: StateFlow<SessionConfig> = mutableConfig.asStateFlow()

    override fun save(config: SessionConfig) {
        preferences.edit {
            putInt(DURATION, config.durationMinutes)
            putInt(INTERVAL, config.intervalMinutes)
        }
        mutableConfig.value = config
    }

    private fun read() = SessionConfig(
        durationMinutes = preferences.getInt(DURATION, SessionConfig().durationMinutes),
        intervalMinutes = preferences.getInt(INTERVAL, SessionConfig().intervalMinutes),
    )

    private companion object {
        const val PREFERENCES = "training_defaults"
        const val DURATION = "duration_minutes"
        const val INTERVAL = "interval_minutes"
    }
}
