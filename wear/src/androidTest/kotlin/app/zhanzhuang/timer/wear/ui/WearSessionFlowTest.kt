package app.zhanzhuang.timer.wear.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import app.zhanzhuang.timer.wear.ui.screens.ActiveSessionScreen
import app.zhanzhuang.timer.wear.ui.theme.ZhanZhuangWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Round-Wear UI contract: a configured active session shows its timer and requires end confirmation. */
@RunWith(AndroidJUnit4::class)
class WearSessionFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun active_session_displays_duration_and_requires_end_confirmation() {
        val config = SessionConfig(durationMinutes = 15)
        rule.setContent {
            ZhanZhuangWearTheme {
                ActiveSessionScreen(
                    state = WearSessionUiState(
                        record = SessionRecord(status = SessionStatus.RUNNING, owner = SessionOwner.WEAR, config = config),
                        remainingMs = config.durationMinutes * 60_000L,
                        currentHeartRateBpm = null,
                        nextReminderAtActiveMs = config.intervalMinutes * 60_000L,
                    ),
                    onPause = {}, onResume = {}, onFinish = {},
                )
            }
        }
        rule.onNodeWithText("Standing").assertIsDisplayed()
        rule.onNodeWithText("15:00").assertIsDisplayed()
        rule.onNodeWithContentDescription("End session").performClick()
        rule.onNodeWithText("End this session?").assertIsDisplayed()
    }
}
