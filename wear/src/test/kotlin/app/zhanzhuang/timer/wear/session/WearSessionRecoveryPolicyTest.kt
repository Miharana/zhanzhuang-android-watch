package app.zhanzhuang.timer.wear.session

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearSessionRecoveryPolicyTest {
    private val preferences: SharedPreferences = ApplicationProvider
        .getApplicationContext<Context>()
        .getSharedPreferences("wear-recovery-policy-test", Context.MODE_PRIVATE)

    @AfterTest
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun freshInstallDoesNotNeedStatusRecoveryOrForegroundService() {
        assertFalse(WearSessionRecoveryPolicy.hasSnapshot(preferences))
        assertFalse(WearSessionRecoveryPolicy.statusRequiresForeground(preferences))
    }

    @Test
    fun activeSnapshotRequiresForegroundRecovery() {
        saveStatus("RUNNING")

        assertTrue(WearSessionRecoveryPolicy.hasSnapshot(preferences))
        assertTrue(WearSessionRecoveryPolicy.statusRequiresForeground(preferences))
    }

    @Test
    fun terminalSnapshotCanPublishStateWithoutForegroundRecovery() {
        saveStatus("COMPLETED")

        assertTrue(WearSessionRecoveryPolicy.hasSnapshot(preferences))
        assertFalse(WearSessionRecoveryPolicy.statusRequiresForeground(preferences))
    }

    @Test
    fun malformedSnapshotNeverRequestsForegroundRecovery() {
        preferences.edit().putString(WearSessionRecoveryPolicy.SNAPSHOT_KEY, "not-json").commit()

        assertTrue(WearSessionRecoveryPolicy.hasSnapshot(preferences))
        assertFalse(WearSessionRecoveryPolicy.statusRequiresForeground(preferences))
    }

    private fun saveStatus(status: String) {
        preferences.edit()
            .putString(
                WearSessionRecoveryPolicy.SNAPSHOT_KEY,
                JSONObject().put("status", status).toString(),
            )
            .commit()
    }
}
