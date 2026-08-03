package app.zhanzhuang.timer.mobile.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import app.zhanzhuang.timer.mobile.MainActivity
import app.zhanzhuang.timer.mobile.data.MobileDatabase
import app.zhanzhuang.timer.mobile.session.MobileSessionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before fun grantNotificationPermissionForTimerFlow() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            "app.zhanzhuang.timer.debug",
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @Test fun light_theme_uses_dark_status_bar_icons() {
        rule.runOnUiThread {
            val controller = WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
            assertTrue(controller.isAppearanceLightStatusBars)
        }
    }

    @Test fun disconnected_phone_can_start_again_after_a_durable_completion() {
        rule.onNodeWithText("Start standing").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Standing").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Standing").assertIsDisplayed()

        rule.onNodeWithText("End session").performClick()
        rule.onNodeWithText("End this session?").assertIsDisplayed()
        rule.onAllNodesWithText("End session")[1].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty() }

        rule.runOnUiThread { rule.activity.recreate() }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty() }
        val completedId = completedRecordId()

        rule.onNodeWithText("Start standing").assertIsDisplayed()
        rule.onNodeWithText("Start standing").performClick()
        try {
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Standing").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError(
                "Second start timed out. Records=${recordStatuses()}\nUI=${rule.onRoot().printToString()}",
                error,
            )
        }
        rule.onNodeWithText("Standing").assertIsDisplayed()
        assertNewActiveRecordPreservesCompletion(completedId)
    }

    private fun recordStatuses(): List<Pair<String, String>> = runBlocking {
        val database = Room.databaseBuilder(rule.activity, MobileDatabase::class.java, "zhan_zhuang.db").build()
        try {
            withTimeout(10_000) { database.sessionDao().observeAll().first() }.map { it.id to it.status }
        } finally {
            database.close()
        }
    }

    private fun completedRecordId(): String = runBlocking {
        val database = Room.databaseBuilder(rule.activity, MobileDatabase::class.java, "zhan_zhuang.db").build()
        try {
            val records = withTimeout(10_000) { database.sessionDao().observeAll().first { it.isNotEmpty() } }
            check(records.single().status == "COMPLETED") { "expected durable COMPLETED record, got ${records.map { it.status }}" }
            records.single().id
        } finally {
            database.close()
        }
    }

    private fun assertNewActiveRecordPreservesCompletion(completedId: String) = runBlocking {
        val database = Room.databaseBuilder(rule.activity, MobileDatabase::class.java, "zhan_zhuang.db").build()
        try {
            val records = withTimeout(10_000) { database.sessionDao().observeAll().first { it.size == 2 } }
            check(records.any { it.id == completedId && it.status == "COMPLETED" }) {
                "completed record $completedId was not preserved: ${records.map { it.id to it.status }}"
            }
            check(records.any { it.id != completedId && it.status in setOf("STARTING", "RUNNING") }) {
                "expected a different active record: ${records.map { it.id to it.status }}"
            }
        } finally {
            database.close()
        }
    }
}
