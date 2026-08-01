package app.zhanzhuang.timer.wear.health

import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServicesException
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.getCurrentExerciseInfo
import androidx.health.services.client.pauseExercise
import androidx.health.services.client.resumeExercise
import androidx.health.services.client.startExercise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class AndroidWearHealthClient(
    private val exerciseClient: ExerciseClient,
) : WearHealthClient {
    private val _updates = MutableSharedFlow<WearHealthUpdate>(extraBufferCapacity = 16)
    override val updates: Flow<WearHealthUpdate> = _updates
    private var ownsExercise = false

    private val callback = object : ExerciseUpdateCallback {
        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            _updates.tryEmit(WearHealthUpdate.AvailabilityChanged(heartRateAvailable = false))
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            update.latestMetrics.getData(DataType.HEART_RATE_BPM).forEach { point ->
                _updates.tryEmit(
                    WearHealthUpdate.HeartRate(
                        bpm = point.value,
                        timeSinceBootMs = point.timeDurationFromBoot.toMillis(),
                    ),
                )
            }
        }

        override fun onLapSummaryReceived(lapSummary: androidx.health.services.client.data.ExerciseLapSummary) = Unit

        override fun onAvailabilityChanged(dataType: androidx.health.services.client.data.DataType<*, *>, availability: Availability) {
            if (dataType == DataType.HEART_RATE_BPM) {
                _updates.tryEmit(WearHealthUpdate.AvailabilityChanged(availability != DataTypeAvailability.UNAVAILABLE))
            }
        }
    }

    override suspend fun start(): WearHealthStartResult {
        ownsExercise = false
        try {
            val info = exerciseClient.getCurrentExerciseInfo()
            val existingExercise = info.toExistingExercise()
            val capabilities = exerciseClient.getCapabilities()
            val result = startResultFor(existingExercise, capabilities.supportsHeartRate())
            if (result !is WearHealthStartResult.Started) return result

            if (existingExercise == ExistingExercise.NONE) {
                exerciseClient.startExercise(createExerciseConfig())
            }
            exerciseClient.setUpdateCallback(callback)
            ownsExercise = true
            return WearHealthStartResult.Started
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            return WearHealthStartResult.DurationOnly(DurationOnlyReason.PERMISSION_DENIED)
        } catch (error: HealthServicesException) {
            return WearHealthStartResult.DurationOnly(DurationOnlyReason.HEALTH_SERVICES_UNAVAILABLE)
        }
    }

    override suspend fun pause() {
        if (!ownsExercise) return
        try {
            exerciseClient.pauseExercise()
        } catch (error: CancellationException) {
            throw error
        }
    }

    override suspend fun resume() {
        if (!ownsExercise) return
        try {
            exerciseClient.resumeExercise()
        } catch (error: CancellationException) {
            throw error
        }
    }

    override suspend fun end() {
        if (!ownsExercise) return
        try {
            exerciseClient.endExercise()
            ownsExercise = false
        } catch (error: CancellationException) {
            throw error
        }
    }
}

@Suppress("RestrictedApi")
private fun androidx.health.services.client.data.ExerciseInfo.toExistingExercise(): ExistingExercise = when (exerciseTrackedStatus) {
    ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS -> ExistingExercise.NONE
    ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS -> ExistingExercise.OWNED
    ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS -> ExistingExercise.OTHER_APP
    else -> ExistingExercise.UNKNOWN
}

private fun ExerciseCapabilities.supportsHeartRate(): Boolean =
    typeToCapabilities[ExerciseType.MEDITATION]
        ?.supportedDataTypes
        ?.contains(DataType.HEART_RATE_BPM) == true
