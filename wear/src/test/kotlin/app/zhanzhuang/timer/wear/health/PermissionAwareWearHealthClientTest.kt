package app.zhanzhuang.timer.wear.health

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionAwareWearHealthClientTest {
    @Test fun deniedPermissionNeverTouchesDelegate() = runTest {
        val delegate = RecordingHealthClient()
        val client = PermissionAwareWearHealthClient(delegate) { false }

        assertEquals(
            WearHealthStartResult.DurationOnly(DurationOnlyReason.PERMISSION_DENIED),
            client.start(),
        )
        assertEquals(
            WearHealthStartResult.DurationOnly(DurationOnlyReason.PERMISSION_DENIED),
            client.reattach(),
        )
        client.pause()
        client.resume()
        client.end()

        assertTrue(delegate.calls.isEmpty())
    }

    @Test fun grantedPermissionDelegatesEveryOperation() = runTest {
        val delegate = RecordingHealthClient()
        val client = PermissionAwareWearHealthClient(delegate) { true }

        assertEquals(WearHealthStartResult.Started, client.start())
        assertEquals(WearHealthStartResult.Started, client.reattach())
        client.pause()
        client.resume()
        client.end()

        assertEquals(listOf("start", "reattach", "pause", "resume", "end"), delegate.calls)
    }

    private class RecordingHealthClient : WearHealthClient {
        override val updates = MutableSharedFlow<WearHealthUpdate>()
        val calls = mutableListOf<String>()
        override suspend fun start(): WearHealthStartResult = WearHealthStartResult.Started.also { calls += "start" }
        override suspend fun reattach(): WearHealthStartResult = WearHealthStartResult.Started.also { calls += "reattach" }
        override suspend fun pause() { calls += "pause" }
        override suspend fun resume() { calls += "resume" }
        override suspend fun end() { calls += "end" }
    }
}
