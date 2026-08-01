package app.zhanzhuang.timer.wear.ui

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import app.zhanzhuang.timer.wear.MainActivity
import app.zhanzhuang.timer.wear.data.WearDatabase
import app.zhanzhuang.timer.wear.session.WearSessionService
import app.zhanzhuang.timer.wear.session.WearSessionUiBridge
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the installed Wear activity and service; it takes duration-only if the API 33 runtime prompt is available. */
@RunWith(AndroidJUnit4::class)
class WearSessionFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun denyHeartRateForDurationOnlyFlow() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.revokeRuntimePermission(
            "app.zhanzhuang.timer.debug",
            Manifest.permission.BODY_SENSORS,
        )
    }

    @After fun stopRealServiceAndRemoveTestDatabase() {
        rule.activity.stopService(Intent(rule.activity, WearSessionService::class.java))
        rule.activity.deleteDatabase("wear-sessions.db")
        rule.activity.getSharedPreferences("wear_session", 0).edit().clear().commit()
    }

    @Test fun real_service_starts_pauses_resumes_finishes_and_persists() {
        rule.onNodeWithContentDescription("Start session").performScrollTo().performTouchInput { click(center) }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("Continue without heart rate").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Standing").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithContentDescription("Continue without heart rate").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithContentDescription("Continue without heart rate").performTouchInput { click(center) }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Standing").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Standing").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause").performTouchInput { click(center) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Paused").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Resume").performTouchInput { click(center) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Standing").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("End session").performTouchInput { click(center) }
        rule.onNodeWithText("End this session?").assertIsDisplayed()
        rule.onNodeWithContentDescription("Confirm end session").performTouchInput { click(center) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty() }
        val sessionId = WearSessionUiBridge.state.value.record?.id ?: error("missing completed Wear session")
        assertCompletedRecordIsDurable(sessionId)
        rule.runOnUiThread { rule.activity.recreate() }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Completed").assertIsDisplayed()
    }

    private fun assertCompletedRecordIsDurable(sessionId: String) = runBlocking {
        val database = Room.databaseBuilder(rule.activity, WearDatabase::class.java, "wear-sessions.db").build()
        try {
            check(database.wearSessionDao().session(sessionId)?.status == "COMPLETED") { "Wear record was not durably completed" }
        } finally {
            database.close()
        }
    }
}
