package app.zhanzhuang.timer.wear

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import app.zhanzhuang.timer.wear.ui.screens.RoundScreenGeometry

class WearStoreScreenContractTest {
    @Test fun startActionPrecedesOptionalDurationShortcuts() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        assertTrue(setup.indexOf("stringResource(R.string.start_session)") < setup.indexOf("stringResource(R.string.shortcuts)"))
    }

    @Test fun setupUsesTheRoundScreenForSessionControlsNotDuplicateBranding() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        assertFalse(setup.contains("R.string.brand_chinese"))
        assertFalse(setup.contains("R.string.standing_meditation"))
    }

    @Test fun permissionContentUsesTheWearShapeAdaptiveList() {
        val permission = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/PermissionScreen.kt").readText()
        assertTrue(permission.contains("rememberTransformingLazyColumnState("))
        assertTrue(permission.contains("TransformingLazyColumn("))
        assertTrue(permission.contains("TransformingLazyColumnDefaults.snapFlingBehavior(state = scroll)"))
        assertTrue(permission.contains("ScreenScaffold(scrollState = scroll"))
        assertTrue(permission.contains("RotaryScrollableDefaults.snapBehavior(scrollableState = scroll)"))
        assertFalse(permission.contains("ScreenColumn("))
    }

    @Test fun sharedWearScreenPartsDoNotKeepTheLegacyRectangularScroller() {
        val screenParts = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/WearScreenParts.kt").readText()
        assertFalse(screenParts.contains("fun ScreenColumn("))
        assertFalse(screenParts.contains("verticalScroll("))
    }

    @Test fun completionUsesTheRoundSafeTransformingList() {
        val completion = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/CompletionScreen.kt").readText()
        assertTrue(completion.contains("rememberTransformingLazyColumnState("))
        assertTrue(completion.contains("TransformingLazyColumn("))
        assertTrue(completion.contains("ScreenScaffold(scrollState = scroll"))
        assertFalse(completion.contains("ScreenColumn("))
    }

    @Test fun wearAppUsesTheMaterial3AppScaffold() {
        val activity = File("src/main/kotlin/app/zhanzhuang/timer/wear/MainActivity.kt").readText()
        assertTrue(activity.contains("AppScaffold {"))
    }

    @Test fun durationShortcutsStayInsideTheRoundScreen() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        assertTrue(setup.contains("RoundActionHorizontalInset, vertical = 2.dp"))
        assertTrue(setup.contains("minimumVerticalContentPadding("))
    }

    @Test fun setupUsesTheWearShapeAdaptiveListWithSnapAndItsOwnIndicator() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        assertTrue(setup.contains("rememberTransformingLazyColumnState("))
        assertTrue(setup.contains("TransformingLazyColumn("))
        assertTrue(setup.contains("TransformingLazyColumnDefaults.snapFlingBehavior(state = scroll)"))
        assertTrue(setup.contains("RotaryScrollableDefaults.snapBehavior(scrollableState = scroll)"))
        assertTrue(setup.contains("ScreenScaffold(scrollState = scroll"))
        assertFalse(setup.contains("ScreenColumn("))
    }

    @Test fun reviewedSetupAndEndConfirmationUseRoundSafeTransformingLists() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        val active = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/ActiveSessionScreen.kt").readText()

        assertTrue(setup.contains("TransformingLazyColumn("))
        assertTrue(setup.contains("RoundStepperHorizontalInset"))
        assertTrue(setup.contains("minimumVerticalContentPadding("))
        assertTrue(setup.contains("applyContentTransformation()"))
        assertTrue(active.contains("rememberTransformingLazyColumnState("))
        assertTrue(active.contains("TransformingLazyColumn("))
        assertTrue(active.contains("applyContentTransformation()"))
        val confirmation = active.substringAfter("private fun EndSessionConfirmation")
        assertTrue(confirmation.contains("TransformingLazyColumn("))
        assertFalse(confirmation.contains("AlertDialog("))
        assertFalse(confirmation.contains("ScreenColumn"))
    }

    @Test fun setupKeepsShortcutHeadingBelowTheInitialRoundSafeViewport() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()

        assertTrue(setup.contains("RoundShortcutTopSafeSpace"))
        assertTrue(File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/WearScreenParts.kt").readText().contains("RoundShortcutTopSafeSpace = 64.dp"))
    }

    @Test fun sharedActionsGiveDefaultFontTextHorizontalRoom() {
        val parts = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/WearScreenParts.kt").readText()
        assertTrue(parts.contains("padding(horizontal = 14.dp, vertical = 4.dp)"))
        assertTrue(parts.contains("textAlign = TextAlign.Center"))
    }

    @Test fun allWearScreenTextPathsUseRoundSafeHorizontalPadding() {
        val files = listOf("SetupScreen.kt", "PermissionScreen.kt", "ActiveSessionScreen.kt", "CompletionScreen.kt")
            .map { File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/$it").readText() }
        files.forEach { source ->
            assertTrue(source.contains("RoundScreenHorizontalPadding"))
            assertTrue(source.contains("transformedHeight("))
            assertTrue(source.contains("textAlign = TextAlign.Center"))
        }
    }

    @Test fun roundSafeActionBoundsFitInsideThe454pxWatchCircle() {
        val safeAction = RoundScreenGeometry.actionBounds(
            diameterPx = 454,
            density = 2f,
            topPx = 323,
        )
        assertTrue(RoundScreenGeometry.rectFitsCircle(safeAction, diameterPx = 454))

        val previousFullWidthAction = RoundScreenGeometry.Rect(left = 36, top = 351, right = 418, bottom = 447)
        assertFalse(RoundScreenGeometry.rectFitsCircle(previousFullWidthAction, diameterPx = 454))
    }
}
