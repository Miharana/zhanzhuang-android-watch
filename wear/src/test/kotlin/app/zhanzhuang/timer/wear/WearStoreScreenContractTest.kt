package app.zhanzhuang.timer.wear

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import app.zhanzhuang.timer.wear.ui.screens.RoundScreenGeometry

class WearStoreScreenContractTest {
    @Test fun startActionPrecedesOptionalDurationShortcuts() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        assertTrue(setup.indexOf("GoldAction(stringResource(R.string.start_session)") < setup.indexOf("Text(stringResource(R.string.shortcuts)"))
    }

    @Test fun setupUsesTheRoundScreenForSessionControlsNotDuplicateBranding() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        assertFalse(setup.contains("R.string.brand_chinese"))
        assertFalse(setup.contains("R.string.standing_meditation"))
    }

    @Test fun permissionContentUsesTheWearShapeAdaptiveList() {
        val permission = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/PermissionScreen.kt").readText()
        assertTrue(permission.contains("rememberScalingLazyListState("))
        assertTrue(permission.contains("ScalingLazyColumn("))
        assertTrue(permission.contains("ScalingLazyColumnDefaults.snapFlingBehavior(state = scroll)"))
        assertTrue(permission.contains("ScrollIndicator(state = scroll"))
        assertFalse(permission.contains("ScreenColumn("))
    }

    @Test fun everyScrollableWearScreenShowsAPlatformScrollIndicator() {
        val screenParts = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/WearScreenParts.kt").readText()
        assertTrue(screenParts.contains("ScrollIndicator("))
        assertTrue(screenParts.contains("state = scrollState"))
        assertTrue(screenParts.contains("Modifier.align(Alignment.CenterEnd)"))
    }

    @Test fun durationShortcutsStayInsideTheRoundScreen() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        assertTrue(setup.contains("modifier = Modifier.padding(horizontal = RoundActionHorizontalInset, vertical = 2.dp)"))
    }

    @Test fun setupUsesTheWearShapeAdaptiveListWithSnapAndItsOwnIndicator() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        assertTrue(setup.contains("rememberScalingLazyListState("))
        assertTrue(setup.contains("ScalingLazyColumn("))
        assertTrue(setup.contains("ScalingLazyColumnDefaults.snapFlingBehavior(state = scroll)"))
        assertTrue(setup.contains("RotaryScrollableDefaults.snapBehavior(scrollableState = scroll)"))
        assertTrue(setup.contains("ScrollIndicator(state = scroll"))
        assertFalse(setup.contains("ScreenColumn("))
    }

    @Test fun roundSafeActionBoundsFitInsideThe454pxWatchCircle() {
        val safeAction = RoundScreenGeometry.actionBounds(
            diameterPx = 454,
            density = 2f,
            topPx = 319,
        )
        assertTrue(RoundScreenGeometry.rectFitsCircle(safeAction, diameterPx = 454))

        val previousFullWidthAction = RoundScreenGeometry.Rect(left = 36, top = 351, right = 418, bottom = 447)
        assertFalse(RoundScreenGeometry.rectFitsCircle(previousFullWidthAction, diameterPx = 454))
    }
}
