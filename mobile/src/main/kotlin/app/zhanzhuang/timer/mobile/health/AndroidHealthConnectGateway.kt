package app.zhanzhuang.timer.mobile.health

import android.content.Context
import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SampleAccuracy
import app.zhanzhuang.timer.model.SessionRecord
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.Device
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalMindfulnessSessionApi::class)
class AndroidHealthConnectGateway(
    private val context: Context,
) : HealthConnectGateway {
    override suspend fun availability(): HealthAvailability = try {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            HealthAvailability.UNAVAILABLE
        } else {
            val client = HealthConnectClient.getOrCreate(context)
            if (
                client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION) ==
                    HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            ) {
                HealthAvailability.AVAILABLE
            } else {
                HealthAvailability.MINDFULNESS_UNSUPPORTED
            }
        }
    } catch (_: Exception) {
        HealthAvailability.UNAVAILABLE
    }

    override fun requiredWritePermissions(includeHeartRate: Boolean): Set<String> =
        writeOnlyPermissions(includeHeartRate)

    override suspend fun write(record: SessionRecord): HealthWriteResult = try {
        writeInternal(record)
    } catch (error: Exception) {
        error.toWriteFailure()
    }

    private suspend fun writeInternal(record: SessionRecord): HealthWriteResult {
        val startTime = record.startEpochMillis?.let(Instant::ofEpochMilli)
        val endTime = record.endEpochMillis?.let(Instant::ofEpochMilli)
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            return HealthWriteResult.PermanentFailure("Completed session has no valid time interval")
        }

        val availability = availability()
        if (availability == HealthAvailability.UNAVAILABLE) {
            return HealthWriteResult.Unavailable("Health Connect is unavailable")
        }

        val client = HealthConnectClient.getOrCreate(context)
        val sessionPermission = sessionWritePermission(availability)
        when (val sessionPermissionState = permissionState(client, setOf(sessionPermission))) {
            HealthPermissionState.Granted -> Unit
            is HealthPermissionState.Missing -> return HealthWriteResult.PermissionMissing(sessionPermissionState.permissions)
        }

        try {
            client.insertRecords(listOf(sessionRecord(record, startTime, endTime, availability)))
        } catch (error: Exception) {
            return error.toWriteFailure()
        }

        val samples = exportableHeartRateSamples(record)
        if (samples.isEmpty()) {
            return HealthWriteResult.Success(sessionWritten = true, heartRateWritten = false)
        }

        val heartRatePermission = HealthPermission.getWritePermission(HeartRateRecord::class)
        if (heartRatePermission !in client.permissionController.getGrantedPermissions()) {
            return HealthWriteResult.Success(sessionWritten = true, heartRateWritten = false)
        }

        return try {
            client.insertRecords(listOf(heartRateRecord(record, startTime, endTime, samples)))
            HealthWriteResult.Success(sessionWritten = true, heartRateWritten = true)
        } catch (error: Exception) {
            error.toWriteFailure()
        }
    }

    private suspend fun permissionState(
        client: HealthConnectClient,
        required: Set<String>,
    ): HealthPermissionState {
        val missing = required - client.permissionController.getGrantedPermissions()
        return if (missing.isEmpty()) HealthPermissionState.Granted else HealthPermissionState.Missing(missing)
    }

    private fun sessionRecord(
        record: SessionRecord,
        startTime: Instant,
        endTime: Instant,
        availability: HealthAvailability,
    ): Record = when (availability) {
        HealthAvailability.AVAILABLE -> MindfulnessSessionRecord(
            startTime = startTime,
            startZoneOffset = null,
            endTime = endTime,
            endZoneOffset = null,
            metadata = metadata(sessionClientRecordId(record), record),
            mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
            title = SESSION_TITLE,
            notes = null,
        )
        HealthAvailability.MINDFULNESS_UNSUPPORTED -> ExerciseSessionRecord(
            startTime = startTime,
            startZoneOffset = null,
            endTime = endTime,
            endZoneOffset = null,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            title = SESSION_TITLE,
            notes = null,
            metadata = metadata(sessionClientRecordId(record), record),
        )
        HealthAvailability.UNAVAILABLE -> error("Availability checked before building record")
    }

    private fun heartRateRecord(
        record: SessionRecord,
        startTime: Instant,
        endTime: Instant,
        samples: List<HeartRateSample>,
    ) = HeartRateRecord(
        startTime = startTime,
        startZoneOffset = null,
        endTime = endTime,
        endZoneOffset = null,
        samples = samples.map { sample ->
            HeartRateRecord.Sample(
                time = Instant.ofEpochMilli(sample.epochMillis),
                beatsPerMinute = sample.bpm.toLong(),
            )
        },
        metadata = metadata(heartRateClientRecordId(record), record),
    )

    private fun metadata(clientRecordId: String, record: SessionRecord): Metadata =
        Metadata.autoRecorded(
            Device(
                type = Device.TYPE_PHONE,
                manufacturer = android.os.Build.MANUFACTURER,
                model = android.os.Build.MODEL,
            ),
            clientRecordId,
            clientRecordVersion(record),
        )

    private fun sessionWritePermission(availability: HealthAvailability): String = when (availability) {
        HealthAvailability.AVAILABLE -> HealthPermission.getWritePermission(MindfulnessSessionRecord::class)
        HealthAvailability.MINDFULNESS_UNSUPPORTED -> HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        HealthAvailability.UNAVAILABLE -> error("Availability checked before requesting permissions")
    }

    private fun Exception.toWriteFailure(): HealthWriteResult = when (this) {
        is IOException -> HealthWriteResult.Retryable(message ?: "Health Connect I/O failure")
        is SecurityException -> HealthWriteResult.PermissionMissing(emptySet())
        else -> HealthWriteResult.PermanentFailure(message ?: javaClass.simpleName)
    }

    companion object {
        private const val SESSION_TITLE = "站桩 / Zhan Zhuang"

        fun writeOnlyPermissions(includeHeartRate: Boolean): Set<String> = buildSet {
            add(HealthPermission.getWritePermission(MindfulnessSessionRecord::class))
            add(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
            if (includeHeartRate) add(HealthPermission.getWritePermission(HeartRateRecord::class))
        }

        fun sessionClientRecordId(record: SessionRecord): String = "zz:${record.id}:session"

        fun heartRateClientRecordId(record: SessionRecord): String = "zz:${record.id}:heart-rate"

        fun clientRecordVersion(record: SessionRecord): Long = record.revision

        fun exportableHeartRateSamples(record: SessionRecord): List<HeartRateSample> {
            val start = record.startEpochMillis ?: return emptyList()
            val end = record.endEpochMillis ?: return emptyList()
            return record.heartRateSamples.filter { sample ->
                sample.epochMillis in start..end &&
                    sample.bpm in 1.0..300.0 &&
                    sample.accuracy in setOf(SampleAccuracy.MEDIUM, SampleAccuracy.HIGH)
            }
        }
    }
}
