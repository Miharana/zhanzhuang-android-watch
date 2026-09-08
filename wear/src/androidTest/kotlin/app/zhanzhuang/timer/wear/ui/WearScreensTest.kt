package app.zhanzhuang.timer.wear.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import app.zhanzhuang.timer.wear.ui.screens.ActiveSessionScreen
import app.zhanzhuang.timer.wear.ui.screens.CompletionScreen
import app.zhanzhuang.timer.wear.ui.screens.SetupScreen
import app.zhanzhuang.timer.wear.ui.theme.ZhanZhuangWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class WearScreensTest {
    @get:Rule val rule = createComposeRule()

    @Test fun setupShowsDefaults() {
        rule.setContent { ZhanZhuangWearTheme { SetupScreen(SessionConfig(), {}, {}) } }

        rule.onAllNodesWithText("30 min")[0].assertIsDisplayed()
        rule.onNodeWithText("Every 10 min").assertIsDisplayed()
    }

    @Test fun endRequiresConfirmation() {
        rule.setContent { ZhanZhuangWearTheme { ActiveSessionScreen(runningState(), {}, {}, {}) } }

        rule.onNodeWithContentDescription("End session").performClick()
        rule.onNodeWithText("End this session?").assertIsDisplayed()
    }

    @Test fun cancelIsSentOnlyFromTheConfirmedEndSurface() {
        var cancelled = false
        rule.setContent {
            ZhanZhuangWearTheme { ActiveSessionScreen(runningState(), {}, {}, {}, onCancel = { cancelled = true }) }
        }

        rule.onNodeWithContentDescription("End session").performClick()
        rule.onNodeWithContentDescription("Cancel session").performClick()

        assertTrue(cancelled)
    }

    @Test fun cancelledSessionIsNotSemanticallyAnnouncedAsCompleted() {
        rule.setContent {
            ZhanZhuangWearTheme { CompletionScreen(terminalState(SessionStatus.CANCELLED), {}) }
        }

        rule.onNodeWithText("Cancelled").assertIsDisplayed()
    }

    private fun runningState() = WearSessionUiState(
        record = SessionRecord(status = SessionStatus.RUNNING, owner = SessionOwner.WEAR),
        remainingMs = 30 * 60_000L,
        currentHeartRateBpm = null,
        nextReminderAtActiveMs = 10 * 60_000L,
    )

    private fun terminalState(status: SessionStatus) = WearSessionUiState(
        record = SessionRecord(status = status, owner = SessionOwner.WEAR, activeDurationMs = 15 * 60_000L),
        remainingMs = 0,
        currentHeartRateBpm = null,
        nextReminderAtActiveMs = null,
    )
}
