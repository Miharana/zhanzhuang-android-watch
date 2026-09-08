package app.zhanzhuang.timer.wear.session

import android.content.SharedPreferences

/**
 * Decides whether a status-only service launch is needed. A fresh launch has
 * no session to recover and must not start a foreground health service merely
 * to render the setup screen.
 */
object WearSessionRecoveryPolicy {
    const val PREFERENCES_NAME = "wear_session"
    const val SNAPSHOT_KEY = "wear_session_snapshot_v1"

    fun hasSnapshot(preferences: SharedPreferences): Boolean = preferences.contains(SNAPSHOT_KEY)

    /**
     * Only an active snapshot needs foreground-service treatment. Terminal
     * snapshots can be published by a normal, activity-bound status launch.
     * Corrupt data fails closed so it can never trigger a risky FGS start.
     */
    fun statusRequiresForeground(preferences: SharedPreferences): Boolean = runCatching {
        val status = preferences.getString(SNAPSHOT_KEY, null)
            ?.let { org.json.JSONObject(it) }
            ?.optString("status")
            ?: return@runCatching false
        status in ACTIVE_STATUSES
    }.getOrDefault(false)

    private val ACTIVE_STATUSES = setOf("STARTING", "RUNNING", "PAUSED", "COMPLETING")
}
