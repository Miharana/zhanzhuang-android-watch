package app.zhanzhuang.timer.mobile.health

import android.content.Context

/**
 * Explicit in-app consent for exporting completed sessions to Health Connect.
 *
 * This is intentionally separate from the Health Connect runtime permission:
 * the app must have both before any foreground or background write can occur.
 */
interface HealthExportConsent {
    fun isAccepted(): Boolean
    fun accept()
}

class SharedPreferencesHealthExportConsent(context: Context) : HealthExportConsent {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun isAccepted(): Boolean = preferences.getBoolean(ACCEPTED_KEY, false)

    override fun accept() {
        preferences.edit().putBoolean(ACCEPTED_KEY, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "health_connect_export"
        const val ACCEPTED_KEY = "disclosure_accepted_v1"
    }
}
