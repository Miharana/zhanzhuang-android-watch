package app.zhanzhuang.timer.wear.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SampleAccuracy
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
import kotlin.test.assertTrue

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
    fun acknowledgmentOnlyRemovesExactlyMatchingRevision() = runTest {
        repository.upsert(record(revision = 2, status = SessionStatus.COMPLETED))
        repository.enqueueCompleted("event-1", "s1", revision = 2, payload = "payload")

        repository.acknowledge("event-1", revision = 1)
        assertEquals(1, repository.pendingOutbox().size)
        repository.acknowledge("event-1", revision = 3)
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

    @Test
    fun completionRecordAndExactRevisionOutboxCommitTogether() = runTest {
        val completed = record(revision = 2, status = SessionStatus.COMPLETED)

        repository.completeWithOutbox(completed, "event-2", "payload")

        assertEquals(completed, repository.get("s1"))
        assertEquals(
            listOf(OutboxEntity(eventId = "event-2", sessionId = "s1", revision = 2, payload = "payload")),
            repository.pendingOutbox(),
        )
    }

    @Test
    fun getUsesOneAtomicAggregateDaoRead() = runTest {
        val samples = listOf(
            HeartRateSample(epochMillis = 1_001, bpm = 60.0, accuracy = SampleAccuracy.HIGH),
            HeartRateSample(epochMillis = 1_002, bpm = 61.0, accuracy = SampleAccuracy.HIGH),
        )
        repository.upsert(record(revision = 2, status = SessionStatus.COMPLETED, samples = samples))
        val aggregateDao = AggregateOnlyWearSessionDao(database.wearSessionDao())
        val aggregateRepository = WearSessionRepository(aggregateDao)

        assertEquals(samples, aggregateRepository.get("s1")!!.heartRateSamples)
        assertEquals(1, aggregateDao.aggregateReadCalls)
    }

    @Test
    fun getOrdersAggregateHeartRateSamplesChronologically() = runTest {
        val samples = listOf(
            HeartRateSample(epochMillis = 1_002, bpm = 61.0, accuracy = SampleAccuracy.HIGH),
            HeartRateSample(epochMillis = 1_001, bpm = 60.0, accuracy = SampleAccuracy.HIGH),
        )
        repository.upsert(record(revision = 2, status = SessionStatus.COMPLETED, samples = samples))
        val unsortedAggregateRepository = WearSessionRepository(
            UnsortedAggregateWearSessionDao(database.wearSessionDao()),
        )

        assertEquals(
            samples.sortedBy(HeartRateSample::epochMillis),
            unsortedAggregateRepository.get("s1")!!.heartRateSamples,
        )
    }

    @Test
    fun equalRevisionReplayAppendsOnlyTheDeltaWithoutReplacingMetadata() = runTest {
        val initial = record(revision = 4, status = SessionStatus.RUNNING)
        val first = HeartRateSample(1_001, 60.0, SampleAccuracy.HIGH)
        val second = HeartRateSample(1_002, 61.0, SampleAccuracy.HIGH)

        repository.checkpoint(initial, listOf(first))
        repository.checkpoint(initial.copy(status = SessionStatus.PAUSED), listOf(second))

        val stored = repository.get("s1")!!
        assertEquals(SessionStatus.RUNNING, stored.status)
        assertEquals(listOf(first, second), stored.heartRateSamples)
    }

    @Test
    fun staleCheckpointCannotAppendSamplesOrReplaceMetadata() = runTest {
        val durable = record(revision = 4, status = SessionStatus.RUNNING)
        val stale = HeartRateSample(1_001, 60.0, SampleAccuracy.HIGH)

        repository.checkpoint(durable, emptyList())
        repository.checkpoint(record(revision = 3, status = SessionStatus.PAUSED), listOf(stale))

        val stored = repository.get("s1")!!
        assertEquals(SessionStatus.RUNNING, stored.status)
        assertTrue(stored.heartRateSamples.isEmpty())
    }

    private fun record(
        revision: Long,
        status: SessionStatus,
        samples: List<HeartRateSample> = emptyList(),
    ) = SessionRecord(
        id = "s1",
        revision = revision,
        config = SessionConfig(15, 5),
        status = status,
        owner = SessionOwner.WEAR,
        startEpochMillis = 1_000,
        heartRateSamples = samples,
    )

    private class AggregateOnlyWearSessionDao(
        private val delegate: WearSessionDao,
    ) : WearSessionDao by delegate {
        var aggregateReadCalls = 0

        override suspend fun sessionWithSamples(id: String): WearSessionWithSamples? {
            aggregateReadCalls += 1
            return delegate.sessionWithSamples(id)
        }

        override suspend fun session(id: String): WearSessionEntity? =
            error("Repository get must use the aggregate DAO read")

        override suspend fun samples(sessionId: String): List<WearHeartRateEntity> =
            error("Repository get must use the aggregate DAO read")
    }

    private class UnsortedAggregateWearSessionDao(
        private val delegate: WearSessionDao,
    ) : WearSessionDao by delegate {
        override suspend fun sessionWithSamples(id: String): WearSessionWithSamples? =
            delegate.sessionWithSamples(id)?.let { aggregate ->
                aggregate.copy(samples = aggregate.samples.sortedByDescending { it.epochMillis })
            }
    }
}
