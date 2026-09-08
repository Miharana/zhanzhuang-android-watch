package app.zhanzhuang.timer.mobile.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthExportConsentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @AfterTest
    fun clearConsent() {
        context.getSharedPreferences("health_connect_export", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun consentIsDeniedByDefault() {
        assertFalse(SharedPreferencesHealthExportConsent(context).isAccepted())
    }

    @Test
    fun acceptancePersistsAcrossInstances() {
        SharedPreferencesHealthExportConsent(context).accept()

        assertTrue(SharedPreferencesHealthExportConsent(context).isAccepted())
    }
}
