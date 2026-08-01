package app.zhanzhuang.timer.wear.sync

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SyncEnvelope
import app.zhanzhuang.timer.model.SyncPayload
import app.zhanzhuang.timer.model.SyncTransport
import app.zhanzhuang.timer.wear.data.WearDatabase
import app.zhanzhuang.timer.wear.data.WearSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearServiceSyncControllerTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        WearDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val repository = WearSessionRepository(database)

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun startForAIsNotConfirmedWhenBIsTheDurableActiveSession() = runTest {
        repository.upsert(record("session-b", SessionStatus.RUNNING, revision = 3))
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val transport = RecordingTransport()
        val coordinator = WearSyncCoordinator(
            outbox = EmptyOutbox,
            transport = transport,
            controller = WearServiceSyncController(context, repository),
        )

        val handled = coordinator.receiveCommand(
            SyncEnvelope(
                eventId = "start-a",
                sessionId = "session-a",
                revision = 1,
                sentAtEpochMillis = 1_000,
                payload = SyncPayload.Start(SessionConfig(), expiresAtEpochMillis = Long.MAX_VALUE),
            ),
        )

        assertFalse(handled)
        assertTrue(context.startedIntents.isEmpty())
        assertTrue(transport.messages.isEmpty())
    }

    @Test
    fun duplicateStartForAnExistingTerminalSessionDoesNotStartTheServiceAgain() = runTest {
        val terminal = record("session-a", SessionStatus.COMPLETED, revision = 8)
        repository.upsert(terminal)
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val controller = WearServiceSyncController(context, repository)

        val result = controller.startFromRemote(SessionConfig(), "session-a", revision = 1)

        assertEquals(terminal, result)
        assertTrue(context.startedIntents.isEmpty())
        assertEquals(terminal, repository.get("session-a"))
    }

    @Test
    fun alreadyAppliedPauseResumeAndFinishReturnDurableStateWithoutStartingService() = runTest {
        val paused = record("paused", SessionStatus.PAUSED, revision = 3)
        val running = record("running", SessionStatus.RUNNING, revision = 4)
        val terminal = record("terminal", SessionStatus.COMPLETED, revision = 5)
        repository.upsert(paused)
        repository.upsert(running)
        repository.upsert(terminal)
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val controller = WearServiceSyncController(context, repository)

        assertEquals(paused, controller.pauseFromRemote("paused"))
        assertEquals(running, controller.resumeFromRemote("running"))
        assertEquals(terminal, controller.finishFromRemote("terminal", cancelled = false))
        assertTrue(context.startedIntents.isEmpty())
    }

    private fun record(id: String, status: SessionStatus, revision: Long) = SessionRecord(
        id = id,
        revision = revision,
        config = SessionConfig(),
        status = status,
        owner = SessionOwner.WEAR,
        startEpochMillis = 1_000,
    )

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val startedIntents = mutableListOf<Intent>()

        override fun startForegroundService(service: Intent): ComponentName? {
            startedIntents += Intent(service)
            return ComponentName(packageName, "WearServiceSyncControllerTest")
        }
    }

    private class RecordingTransport : SyncTransport {
        val messages = mutableListOf<SyncEnvelope>()
        override suspend fun sendMessage(envelope: SyncEnvelope): Boolean {
            messages += envelope
            return true
        }
        override suspend fun putCompleted(envelope: SyncEnvelope): Boolean = true
    }

    private object EmptyOutbox : WearCompletionOutbox {
        override suspend fun pendingCompleted(): List<OutboxEntry> = emptyList()
        override suspend fun acknowledge(eventId: String, revision: Long) = Unit
    }
}
