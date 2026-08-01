package app.zhanzhuang.timer.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.zhanzhuang.timer.mobile.ui.screens.TrainingScreen
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** User-visible setup → running → pause → resume → completed flow without device-only claims. */
@RunWith(AndroidJUnit4::class)
class MobileSessionFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun session_controls_complete_one_local_phone_session() {
        rule.setContent {
            var state by remember { mutableStateOf(TrainingUiState(watchConnected = false)) }
            fun running(status: SessionStatus) = state.copy(
                session = SessionRecord(status = status, owner = SessionOwner.MOBILE),
                remainingMs = 30 * 60_000L,
            )
            TrainingScreen(
                state = state,
                onDurationChange = { state = state.copy(config = state.config.copy(durationMinutes = it)) },
                onIntervalChange = { state = state.copy(config = state.config.copy(intervalMinutes = it)) },
                onStart = { state = running(SessionStatus.RUNNING) },
                onPause = { state = running(SessionStatus.PAUSED) },
                onResume = { state = running(SessionStatus.RUNNING) },
                onFinish = { state = state.copy(session = state.session?.copy(status = SessionStatus.COMPLETED)) },
                onCancel = { state = state.copy(session = state.session?.copy(status = SessionStatus.CANCELLED)) },
            )
        }

        rule.onNodeWithText("Start standing").performClick()
        rule.onNodeWithText("Standing").assertIsDisplayed()
        rule.onNodeWithText("Pause").performClick()
        rule.onNodeWithText("Paused").assertIsDisplayed()
        rule.onNodeWithText("Resume").performClick()
        rule.onNodeWithText("End session").performClick()
        rule.onNodeWithText("End this session?").assertIsDisplayed()
        rule.onAllNodesWithText("End session")[1].performClick()
        rule.onNodeWithText("Completed").assertIsDisplayed()
    }
}
