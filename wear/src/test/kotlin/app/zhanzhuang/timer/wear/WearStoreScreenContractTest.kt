package app.zhanzhuang.timer.wear

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class WearStoreScreenContractTest {
    @Test fun startActionPrecedesOptionalDurationShortcuts() {
        val setup = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/SetupScreen.kt").readText()
        assertTrue(setup.indexOf("GoldAction(stringResource(R.string.start_session)") < setup.indexOf("Text(stringResource(R.string.shortcuts)"))
    }

    @Test fun permissionContentUsesRoundScreenSafeTopAndButtonSpacing() {
        val permission = File("src/main/kotlin/app/zhanzhuang/timer/wear/ui/screens/PermissionScreen.kt").readText()
        assertTrue(permission.contains("ScreenColumn(verticalSpacing = 4.dp)"))
        assertTrue(permission.contains("Spacer(Modifier.height(36.dp))"))
    }
}
