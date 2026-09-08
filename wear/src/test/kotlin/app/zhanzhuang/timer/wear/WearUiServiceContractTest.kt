package app.zhanzhuang.timer.wear

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.wear.session.WearSessionService
import app.zhanzhuang.timer.wear.session.WearSessionUiBridge
import app.zhanzhuang.timer.wear.session.WearSessionUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearUiServiceContractTest {
    @Test fun activeServicePublishesDurableStateBeforeRuntimeUpdates() {
        val source = File("src/main/kotlin/app/zhanzhuang/timer/wear/session/WearSessionService.kt").readText()
        val runtimePublisher = source
            .substringAfter("private fun scheduleRuntimePublish")
            .substringBefore("private suspend fun publishTerminalState")
        val statePublish = runtimePublisher.indexOf("publishState(record)")
        val runtimePublish = runtimePublisher.indexOf("publishRuntime(runtime)")

        assertTrue(statePublish >= 0, "A watch-local start must announce its durable state to the phone")
        assertTrue(runtimePublish > statePublish, "Runtime updates must follow the authoritative session state")
    }

    @Test fun manifestDeclaresPermissionSafeForegroundTypes() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"health|specialUse\""))
        assertTrue(manifest.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
        assertTrue(manifest.contains("android:value=\"standing_meditation_timer\""))
    }

    @Test fun explicitSetupStartUsesTheServiceStartContract() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = WearSessionService.startIntent(context, SessionConfig(45, 15), "ui-session")

        val command = WearSessionService.commandFrom(intent)

        val start = assertIs<WearSessionService.Command.Start>(command)
        assertEquals("ui-session", start.sessionId)
        assertEquals(SessionConfig(45, 15), start.config)
    }

    @Test fun uiBridgeExposesTheControllerOwnedStateWithoutATimerCopy() {
        val state = WearSessionUiState(record = null, remainingMs = 0, currentHeartRateBpm = null, nextReminderAtActiveMs = null)

        WearSessionUiBridge.publish(state)

        assertEquals(state, WearSessionUiBridge.state.value)
    }
}
