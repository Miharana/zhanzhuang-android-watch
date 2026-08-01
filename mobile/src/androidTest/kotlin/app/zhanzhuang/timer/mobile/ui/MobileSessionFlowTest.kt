package app.zhanzhuang.timer.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.zhanzhuang.timer.mobile.MainActivity
import app.zhanzhuang.timer.mobile.data.MobileDatabase
import app.zhanzhuang.timer.mobile.session.MobileSessionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the installed activity, its ViewModel/coordinator, and the real phone FGS; no callback injection. */
@RunWith(AndroidJUnit4::class)
class MobileSessionFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun stopRealServiceAndRemoveTestDatabase() {
        rule.activity.stopService(android.content.Intent(rule.activity, MobileSessionService::class.java))
        rule.activity.deleteDatabase("zhan_zhuang.db")
    }

    @Test fun disconnected_phone_start_finishes_and_is_visible_in_history() {
        rule.onNodeWithText("Start standing").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Standing").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Standing").assertIsDisplayed()

        rule.onNodeWithText("End session").performClick()
        rule.onNodeWithText("End this session?").assertIsDisplayed()
        rule.onAllNodesWithText("End session")[1].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty() }

        rule.runOnUiThread { rule.activity.recreate() }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty() }
        assertCompletedRecordIsDurable()
    }

    private fun assertCompletedRecordIsDurable() = runBlocking {
        val database = Room.databaseBuilder(rule.activity, MobileDatabase::class.java, "zhan_zhuang.db").build()
        try {
            val records = withTimeout(10_000) { database.sessionDao().observeAll().first { it.isNotEmpty() } }
            check(records.single().status == "COMPLETED") { "expected durable COMPLETED record, got ${records.map { it.status }}" }
        } finally {
            database.close()
        }
    }
}
