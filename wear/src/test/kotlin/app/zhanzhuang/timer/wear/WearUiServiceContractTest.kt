package app.zhanzhuang.timer.wear

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.wear.session.WearSessionService
import app.zhanzhuang.timer.wear.session.WearSessionUiBridge
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearUiServiceContractTest {
    @Test fun explicitSetupStartUsesTheServiceStartContract() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = WearSessionService.startIntent(context, SessionConfig(45, 15), "ui-session")

        val command = WearSessionService.commandFrom(intent)

        val start = assertIs<WearSessionService.Command.Start>(command)
        assertEquals("ui-session", start.sessionId)
        assertEquals(SessionConfig(45, 15), start.config)
    }

    @Test fun uiBridgeExposesTheControllerOwnedStateWithoutATimerCopy() {
        val state = WearSessionUiState(record = null, remainingMs = 0, currentHeartRateBpm = null, nextReminderAtActiveMs = null)

        WearSessionUiBridge.publish(state)

        assertEquals(state, WearSessionUiBridge.state.value)
    }
}
