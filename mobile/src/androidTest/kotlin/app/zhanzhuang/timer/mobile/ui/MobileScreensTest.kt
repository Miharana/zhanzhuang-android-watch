package app.zhanzhuang.timer.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.mobile.ui.screens.SessionDetailScreen
import app.zhanzhuang.timer.mobile.ui.screens.TrainingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileScreensTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun disconnectedWatchExplainsMissingHeartRate() {
        rule.setContent {
            TrainingScreen(
                state = TrainingUiState(config = SessionConfig(), watchConnected = false),
                onDurationChange = {},
                onIntervalChange = {},
                onStart = {},
                onPause = {},
                onResume = {},
                onFinish = {},
                onCancel = {},
            )
        }

        rule.onNodeWithText("This session won't record heart rate").assertIsDisplayed()
    }

    @Test
    fun chartHasAReadableAccessibilitySummary() {
        rule.setContent {
            SessionDetailScreen(
                record = completedRecord(),
                onBack = {},
            )
        }

        rule.onNodeWithContentDescription("Heart-rate chart: 2 samples, 64 to 72 bpm").assertIsDisplayed()
    }

    @Test
    fun primaryActionRemainsReachableAtLargeFontScale() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                TrainingScreen(
                    state = TrainingUiState(config = SessionConfig(), watchConnected = true),
                    onDurationChange = {}, onIntervalChange = {}, onStart = {}, onPause = {}, onResume = {}, onFinish = {}, onCancel = {},
                )
            }
        }

        rule.onNodeWithText("Start standing").assertIsDisplayed()
    }

    private fun completedRecord() = app.zhanzhuang.timer.model.SessionRecord(
        config = SessionConfig(),
        status = app.zhanzhuang.timer.model.SessionStatus.COMPLETED,
        startEpochMillis = 1_000L,
        endEpochMillis = 121_000L,
        activeDurationMs = 120_000L,
        heartRateSamples = listOf(
            app.zhanzhuang.timer.model.HeartRateSample(30_000L, 64.0, app.zhanzhuang.timer.model.SampleAccuracy.HIGH),
            app.zhanzhuang.timer.model.HeartRateSample(90_000L, 72.0, app.zhanzhuang.timer.model.SampleAccuracy.HIGH),
        ),
    )
}
