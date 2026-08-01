package app.zhanzhuang.timer.wear.sync

import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WearServiceCommandGatewayTest {
    @Test
    fun dispatchReturnsOnlyTheRecordCompletedByTheServiceActor() = runTest {
        val requestId = CompletableDeferred<String>()
        val persisted = record("session-a", SessionStatus.PAUSED, revision = 7)
        val dispatch = async {
            WearServiceCommandGateway.dispatch { requestId.complete(it) }
        }

        yield()
        WearServiceCommandGateway.complete(requestId.await(), persisted)

        assertEquals(persisted, dispatch.await())
    }

    @Test
    fun dispatchTimeoutReturnsNoSyntheticState() = runTest {
        assertNull(
            WearServiceCommandGateway.dispatch(timeoutMillis = 1) {
                // The service actor never reports a durable post-command record.
            },
        )
    }

    private fun record(id: String, status: SessionStatus, revision: Long) = SessionRecord(
        id = id,
        revision = revision,
        config = SessionConfig(),
        status = status,
        owner = SessionOwner.WEAR,
        startEpochMillis = 1_000,
    )
}
