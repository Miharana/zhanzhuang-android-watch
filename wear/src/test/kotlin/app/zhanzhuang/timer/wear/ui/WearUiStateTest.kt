package app.zhanzhuang.timer.wear.ui

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.ui.screens.completionLabel
import app.zhanzhuang.timer.wear.ui.theme.DeepGold
import app.zhanzhuang.timer.wear.ui.theme.DeepSoil
import app.zhanzhuang.timer.wear.ui.theme.ReflectiveGold
import app.zhanzhuang.timer.wear.ui.theme.WarmPaper
import app.zhanzhuang.timer.wear.ui.theme.ZhanZhuangColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WearUiStateTest {
    @Test
    fun setupUsesApprovedDefaultsAndBounds() {
        val state = WearSetupState()

        assertEquals(SessionConfig(30, 10), state.config)
        assertEquals(15, state.withDurationDelta(-100).config.durationMinutes)
        assertEquals(180, state.withDurationDelta(100).config.durationMinutes)
        assertEquals(5, state.withIntervalDelta(-100).config.intervalMinutes)
        assertEquals(30, state.withIntervalDelta(100).config.intervalMinutes)
    }

    @Test
    fun finishRequiresAnExplicitSecondAction() {
        val state = WearUiReducer.reduce(WearUiState.Active, WearUiAction.RequestFinish)

        assertTrue(state is WearUiState.ConfirmFinish)
        assertEquals(WearUiEffect.None, WearUiReducer.effect(WearUiState.Active, WearUiAction.RequestFinish))
        assertEquals(WearUiEffect.Finish, WearUiReducer.effect(state, WearUiAction.ConfirmFinish))
        assertFalse(WearUiReducer.reduce(state, WearUiAction.DismissFinish) is WearUiState.ConfirmFinish)
    }

    @Test
    fun timerUsesFixedWidthMinuteSecondFormat() {
        assertEquals("30:00", formatWearDuration(30 * 60_000L))
        assertEquals("00:05", formatWearDuration(5_000L))
        assertEquals("00:00", formatWearDuration(-1L))
    }

    @Test fun completionLabelTellsTheTruthForEveryTerminalStatus() {
        assertEquals("Completed", completionLabel(SessionStatus.COMPLETED))
        assertEquals("Cancelled", completionLabel(SessionStatus.CANCELLED))
        assertEquals("Interrupted", completionLabel(SessionStatus.INTERRUPTED))
    }

    @Test fun themeMapsEarthAndGoldTokensToWearSemanticRoles() {
        assertEquals(ReflectiveGold, ZhanZhuangColorScheme.primary)
        assertEquals(DeepSoil, ZhanZhuangColorScheme.onPrimary)
        assertEquals(DeepSoil, ZhanZhuangColorScheme.background)
        assertEquals(WarmPaper, ZhanZhuangColorScheme.onBackground)
        assertEquals(DeepGold, ZhanZhuangColorScheme.outline)
    }
}
