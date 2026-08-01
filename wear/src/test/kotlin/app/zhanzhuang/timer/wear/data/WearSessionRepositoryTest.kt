package app.zhanzhuang.timer.wear.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionOwner
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearSessionRepositoryTest {
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
    fun staleRevisionCannotReplaceDurableOutboxPayload() = runTest {
        repository.upsert(record(revision = 2, status = SessionStatus.COMPLETED))
        repository.enqueueCompleted("event-1", "s1", revision = 2, payload = "new")
        repository.upsert(record(revision = 1, status = SessionStatus.RUNNING))
        repository.enqueueCompleted("event-1", "s1", revision = 1, payload = "stale")

        assertEquals("new", repository.pendingOutbox().single().payload)
        assertEquals(SessionStatus.COMPLETED, repository.get("s1")!!.status)
    }

    @Test
    fun acknowledgmentOnlyRemovesMatchingOrNewerRevision() = runTest {
        repository.upsert(record(revision = 2, status = SessionStatus.COMPLETED))
        repository.enqueueCompleted("event-1", "s1", revision = 2, payload = "payload")

        repository.acknowledge("event-1", revision = 1)
        assertEquals(1, repository.pendingOutbox().size)
        repository.acknowledge("event-1", revision = 2)

        assertNull(repository.pendingOutbox().singleOrNull())
    }

    @Test
    fun outboxCannotClaimAnUnpersistedFutureRevision() = runTest {
        repository.upsert(record(revision = 2, status = SessionStatus.COMPLETED))

        repository.enqueueCompleted("event-1", "s1", revision = 3, payload = "future")

        assertNull(repository.pendingOutbox().singleOrNull())
    }

    @Test
    fun newerSessionRevisionDoesNotDeleteAnUnacknowledgedOutboxEvent() = runTest {
        repository.upsert(record(revision = 2, status = SessionStatus.COMPLETED))
        repository.enqueueCompleted("event-2", "s1", revision = 2, payload = "revision-2")

        repository.upsert(record(revision = 3, status = SessionStatus.COMPLETED))

        assertEquals(
            listOf(OutboxEntity(eventId = "event-2", sessionId = "s1", revision = 2, payload = "revision-2")),
            repository.pendingOutbox(),
        )
    }

    private fun record(revision: Long, status: SessionStatus) = SessionRecord(
        id = "s1",
        revision = revision,
        config = SessionConfig(15, 5),
        status = status,
        owner = SessionOwner.WEAR,
        startEpochMillis = 1_000,
    )
}
