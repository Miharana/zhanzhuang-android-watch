package app.zhanzhuang.timer.wear.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.wear.MainActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPendingIntent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OngoingSessionNotificationTest {
    @Test fun contentIntentReopensMainActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val pending = OngoingSessionNotification(context).openActivityIntent()
        val shadow: ShadowPendingIntent = Shadows.shadowOf(pending)

        assertTrue(pending.isActivity)
        assertEquals(MainActivity::class.java.name, shadow.savedIntent.component?.className)
        assertTrue(shadow.savedIntent.flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }
}
