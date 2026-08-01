package app.zhanzhuang.timer.wear.ui.screens

import androidx.compose.ui.tooling.preview.Preview
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import app.zhanzhuang.timer.wear.ui.theme.ZhanZhuangWearTheme

@Preview(name = "Setup 41 mm round", device = "id:wearos_small_round", showBackground = true)
@Preview(name = "Setup 45 mm round", device = "id:wearos_large_round", showBackground = true)
@androidx.compose.runtime.Composable
private fun SetupRoundPreview() {
    ZhanZhuangWearTheme { SetupScreen(SessionConfig(), {}, {}) }
}

@Preview(name = "Active 41 mm round", device = "id:wearos_small_round", showBackground = true)
@Preview(name = "Active 45 mm round", device = "id:wearos_large_round", showBackground = true)
@androidx.compose.runtime.Composable
private fun ActiveRoundPreview() {
    ZhanZhuangWearTheme {
        ActiveSessionScreen(
            state = WearSessionUiState(
                record = SessionRecord(config = SessionConfig(30, 10), status = SessionStatus.RUNNING, owner = SessionOwner.WEAR),
                remainingMs = 28 * 60_000L + 12_000L,
                currentHeartRateBpm = 72.0,
                nextReminderAtActiveMs = 10 * 60_000L,
            ),
            onPause = {},
            onResume = {},
            onFinish = {},
        )
    }
}
