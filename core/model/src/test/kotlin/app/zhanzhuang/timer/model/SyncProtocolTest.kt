package app.zhanzhuang.timer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncProtocolTest {
    @Test
    fun completedRecordRoundTripsThroughGzipOutboxPayload() {
        val envelope = SyncEnvelope(
            eventId = "event-1",
            sessionId = "session-1",
            revision = 4,
            sentAtEpochMillis = 1_000,
            payload = SyncPayload.Completed(
                SessionRecord(
                    id = "session-1",
                    revision = 4,
                    status = SessionStatus.COMPLETED,
                    owner = SessionOwner.WEAR,
                    startEpochMillis = 1_000,
                    endEpochMillis = 2_000,
                ),
            ),
        )

        assertEquals(envelope, SyncWireCodec.decodeCompletedFromOutbox(SyncWireCodec.encodeCompletedForOutbox(envelope)))
    }

    @Test
    fun rejectsOversizedAndCorruptWireInputsBeforeAllocation() {
        assertFailsWith<IllegalArgumentException> {
            SyncWireCodec.decodeMessage(ByteArray(SyncWireCodec.MAX_MESSAGE_BYTES + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            SyncWireCodec.decodeCompleted(ByteArray(SyncWireCodec.MAX_COMPLETED_COMPRESSED_BYTES + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            SyncWireCodec.decodeCompleted(byteArrayOf(1, 2, 3))
        }
    }

    @Test
    fun runtimeAnchorSupportsTheWearStartingRecoveryBoundary() {
        val runtime = SessionRuntime(
            sessionId = "session-1",
            revision = 1,
            status = SessionStatus.STARTING,
            activeDurationMs = 0,
            remainingMs = 1_800_000,
            reportedAtEpochMillis = 1_000,
        )

        assertEquals(SessionStatus.STARTING, runtime.status)
    }

    @Test
    fun threeHourHeartRateTransferIsBoundedAndKeepsEndpoints() {
        val samples = (0 until 10_800).map { HeartRateSample(1_000L + it * 1_000L, 60.0 + it % 5, SampleAccuracy.HIGH) }
        val envelope = SyncEnvelope(eventId = "event", sessionId = "session", revision = 4, sentAtEpochMillis = 1_000, payload = SyncPayload.Completed(
            SessionRecord(id = "session", revision = 4, status = SessionStatus.COMPLETED, owner = SessionOwner.WEAR,
                startEpochMillis = samples.first().epochMillis, endEpochMillis = samples.last().epochMillis, activeDurationMs = 10_800_000,
                heartRateSamples = samples),
        ))

        val decoded = SyncWireCodec.decodeCompleted(SyncWireCodec.encodeCompleted(envelope))
        val transferred = (decoded.payload as SyncPayload.Completed).record.heartRateSamples
        assertTrue(transferred.size <= SyncWireCodec.MAX_COMPLETED_SAMPLES)
        assertEquals(samples.first(), transferred.first())
        assertEquals(samples.last(), transferred.last())
        assertTrue(transferred.zipWithNext().all { (a, b) -> a.epochMillis < b.epochMillis })
    }
}
