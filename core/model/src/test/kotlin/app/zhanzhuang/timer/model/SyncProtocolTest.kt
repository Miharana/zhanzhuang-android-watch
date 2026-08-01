package app.zhanzhuang.timer.model

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
