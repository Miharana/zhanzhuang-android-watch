package app.zhanzhuang.timer.mobile.health

import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SampleAccuracy
import app.zhanzhuang.timer.model.SessionConfig
import app.zhanzhuang.timer.model.SessionRecord
import app.zhanzhuang.timer.model.SessionStatus
import app.zhanzhuang.timer.model.SyncState
import android.os.RemoteException
import kotlinx.coroutines.CancellationException
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HealthSyncWorkerTest {
    @Test
    fun mindfulnessPreferredAndHeartRateAssociated() = runTest {
        val gateway = FakeHealthGateway(mindfulnessSupported = true)

        val outcome = worker(gateway).sync(completedWithSamples())

        assertIs<HealthSyncOutcome.Synced>(outcome)
        assertEquals(listOf(WriteKind.MINDFULNESS, WriteKind.HEART_RATE), gateway.writes)
    }

    @Test
    fun unsupportedMindfulnessFallsBackToOtherWorkout() = runTest {
        val gateway = FakeHealthGateway(mindfulnessSupported = false)

        val outcome = worker(gateway).sync(completed())

        assertIs<HealthSyncOutcome.Synced>(outcome)
        assertEquals(listOf(WriteKind.EXERCISE), gateway.writes)
    }

    @Test
    fun productionRecordMappingPrefersMindfulnessAndFallsBackToExercise() {
        assertEquals(
            HealthSessionRecordKind.MINDFULNESS,
            AndroidHealthConnectGateway.sessionRecordKind(HealthAvailability.AVAILABLE),
        )
        assertEquals(
            HealthSessionRecordKind.EXERCISE,
            AndroidHealthConnectGateway.sessionRecordKind(HealthAvailability.MINDFULNESS_UNSUPPORTED),
        )
    }

    @Test
    fun shortSessionStaysLocal() = runTest {
        val gateway = FakeHealthGateway()

        val outcome = worker(gateway).sync(completed(activeDurationMillis = 59_999))

        assertIs<HealthSyncOutcome.Skipped>(outcome)
        assertTrue(gateway.writes.isEmpty())
    }

    @Test
    fun backgroundSyncSkipsBeforeDisclosureAcceptance() = runTest {
        val gateway = FakeHealthGateway()

        val outcome = worker(gateway, FakeConsent(accepted = false)).sync(completed())

        assertIs<HealthSyncOutcome.Skipped>(outcome)
        assertTrue(gateway.writes.isEmpty())
    }

    @Test
    fun missingWritePermissionDoesNotRetryOrWrite() = runTest {
        val gateway = FakeHealthGateway(result = HealthWriteResult.PermissionMissing(setOf("write:mindfulness")))

        val outcome = worker(gateway).sync(completed())

        assertEquals(HealthSyncOutcome.PermissionMissing(setOf("write:mindfulness")), outcome)
        assertEquals(listOf(WriteKind.MINDFULNESS), gateway.writes)
    }

    @Test
    fun partialHeartRateWriteIsStillSynced() = runTest {
        val gateway = FakeHealthGateway(
            result = HealthWriteResult.Success(sessionWritten = true, heartRateWritten = false),
        )

        val outcome = worker(gateway).sync(completedWithSamples())

        assertEquals(HealthSyncOutcome.Synced(sessionWritten = true, heartRateWritten = false), outcome)
    }

    @Test
    fun transientFailureRequestsExponentialRetry() = runTest {
        val gateway = FakeHealthGateway(result = HealthWriteResult.Retryable("temporarily unavailable"))

        val outcome = worker(gateway).sync(completed())

        assertEquals(HealthSyncOutcome.Retry, outcome)
    }

    @Test
    fun successfulSessionWithoutHeartRateSamplesIsMarkedSynced() {
        assertEquals(
            SyncState.SYNCED,
            HealthSyncWorker.nextHealthConnectState(
                completed(),
                HealthSyncOutcome.Synced(sessionWritten = true, heartRateWritten = false),
            ),
        )
    }

    @Test
    fun partialHeartRateWriteStaysFailedForManualRetry() {
        assertEquals(
            SyncState.FAILED,
            HealthSyncWorker.nextHealthConnectState(
                completedWithSamples(),
                HealthSyncOutcome.Synced(sessionWritten = true, heartRateWritten = false),
            ),
        )
    }

    @Test
    fun remoteHealthConnectFailureIsRetryable() {
        assertIs<HealthWriteResult.Retryable>(
            AndroidHealthConnectGateway.writeFailure(RemoteException("binder disconnected")),
        )
    }

    @Test
    fun userStartedSessionsUseActivelyRecordedMetadata() {
        val metadata = AndroidHealthConnectGateway.sessionMetadata(
            completed(),
            androidx.health.connect.client.records.metadata.Device(
                androidx.health.connect.client.records.metadata.Device.TYPE_PHONE, "test", "test",
            ),
        )

        assertEquals(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED, metadata.recordingMethod)
        assertEquals("zz:session-1:session", metadata.clientRecordId)
        assertEquals(7, metadata.clientRecordVersion)
    }

    @Test
    fun writeFailureRethrowsCancellationInsteadOfConvertingIt() {
        assertFailsWith<CancellationException> {
            AndroidHealthConnectGateway.writeFailure(CancellationException("cancelled"))
        }
    }

    @Test
    fun workerOutcomeAdvancesThePendingRevision() {
        val pending = completed().copy(revision = 8, healthConnectState = SyncState.PENDING)

        val updated = HealthSyncWorker.recordAfterOutcome(
            pending,
            HealthSyncOutcome.Synced(sessionWritten = true, heartRateWritten = false),
        )

        assertEquals(9, updated?.revision)
        assertEquals(SyncState.SYNCED, updated?.healthConnectState)
    }

    @Test
    fun reconciliationReenqueuesOnlyEligiblePendingCompletions() {
        val pending = completed().copy(healthConnectState = SyncState.PENDING)
        val shortPending = completed(activeDurationMillis = 30_000).copy(healthConnectState = SyncState.PENDING)

        assertEquals(listOf(pending), HealthSyncWorker.reconciliationCandidates(listOf(pending, shortPending)))
    }

    @Test
    fun requiredPermissionsAreWriteOnly() {
        val permissions = AndroidHealthConnectGateway.writeOnlyPermissions(includeHeartRate = true)

        assertTrue(permissions.isNotEmpty())
        assertTrue(permissions.all { it.contains("WRITE_") })
    }

    @Test
    fun clientRecordIdsAndVersionsAreDeterministic() {
        val record = completed()

        assertEquals("zz:session-1:session", AndroidHealthConnectGateway.sessionClientRecordId(record))
        assertEquals("zz:session-1:heart-rate", AndroidHealthConnectGateway.heartRateClientRecordId(record))
        assertEquals(7, AndroidHealthConnectGateway.clientRecordVersion(record))
    }

    @Test
    fun onlyValidAccurateHeartRateSamplesInsideSessionAreExported() {
        val record = completed(
            samples = listOf(
                HeartRateSample(999, 80.0, SampleAccuracy.HIGH),
                HeartRateSample(1_000, 0.0, SampleAccuracy.HIGH),
                HeartRateSample(1_200, 100.0, SampleAccuracy.LOW),
                HeartRateSample(1_400, 101.0, SampleAccuracy.MEDIUM),
                HeartRateSample(1_600, 301.0, SampleAccuracy.HIGH),
                HeartRateSample(61_001, 102.0, SampleAccuracy.HIGH),
            ),
        )

        assertEquals(
            listOf(HeartRateSample(1_400, 101.0, SampleAccuracy.MEDIUM)),
            AndroidHealthConnectGateway.exportableHeartRateSamples(record),
        )
    }

    private fun worker(gateway: HealthConnectGateway, consent: FakeConsent = FakeConsent(accepted = true)) =
        HealthSyncWorker(gateway, consent)

    private class FakeConsent(private var accepted: Boolean) : HealthExportConsent {
        override fun isAccepted(): Boolean = accepted
        override fun accept() { accepted = true }
    }

    private fun completedWithSamples() = completed(
        samples = listOf(
            HeartRateSample(1_000, 80.0, SampleAccuracy.HIGH),
            HeartRateSample(1_500, 90.0, SampleAccuracy.MEDIUM),
        ),
    )

    private fun completed(
        activeDurationMillis: Long = 60_000,
        samples: List<HeartRateSample> = emptyList(),
    ) = SessionRecord(
        id = "session-1",
        revision = 7,
        config = SessionConfig(15, 5),
        status = SessionStatus.COMPLETED,
        startEpochMillis = 1_000,
        endEpochMillis = 1_000 + activeDurationMillis,
        activeDurationMs = activeDurationMillis,
        heartRateSamples = samples,
    )

    private class FakeHealthGateway(
        mindfulnessSupported: Boolean = true,
        private val result: HealthWriteResult = HealthWriteResult.Success(
            sessionWritten = true,
            heartRateWritten = true,
        ),
    ) : HealthConnectGateway {
        private val availability = if (mindfulnessSupported) {
            HealthAvailability.AVAILABLE
        } else {
            HealthAvailability.MINDFULNESS_UNSUPPORTED
        }
        val writes = mutableListOf<WriteKind>()

        override suspend fun availability(): HealthAvailability = availability

        override fun requiredWritePermissions(includeHeartRate: Boolean): Set<String> = emptySet()

        override suspend fun write(record: SessionRecord): HealthWriteResult {
            writes += if (availability == HealthAvailability.AVAILABLE) {
                WriteKind.MINDFULNESS
            } else {
                WriteKind.EXERCISE
            }
            if (record.heartRateSamples.isNotEmpty() && result !is HealthWriteResult.PermissionMissing) {
                writes += WriteKind.HEART_RATE
            }
            return result
        }
    }
}

private enum class WriteKind { MINDFULNESS, EXERCISE, HEART_RATE }
