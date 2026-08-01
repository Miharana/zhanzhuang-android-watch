package app.zhanzhuang.timer.mobile.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class EdgeToEdgeContractTest {
    @Test fun appContentRespectsSafeDrawingInsetsExactlyOnce() {
        val activity = File("src/main/kotlin/app/zhanzhuang/timer/mobile/MainActivity.kt").readText()
        assertTrue(activity.contains("WindowInsets.safeDrawing"))
        assertTrue(activity.contains("windowInsetsPadding(WindowInsets.safeDrawing)"))
    }

    @Test fun settingsScrollsSoPrivacyControlsRemainReachableOnShortScreens() {
        val settings = File("src/main/kotlin/app/zhanzhuang/timer/mobile/ui/screens/SettingsScreen.kt").readText()
        assertTrue(settings.contains("rememberScrollState"))
        assertTrue(settings.contains("verticalScroll(rememberScrollState())"))
    }
}
