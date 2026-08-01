package app.zhanzhuang.timer.wear.health

import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseType
import kotlinx.coroutines.flow.Flow

sealed interface WearHealthUpdate {
    data class HeartRate(
        val bpm: Double,
        /** Elapsed milliseconds since boot; callers must not treat this as wall-clock time. */
        val timeSinceBootMs: Long,
    ) : WearHealthUpdate

    data class AvailabilityChanged(val heartRateAvailable: Boolean) : WearHealthUpdate
}

sealed interface WearHealthStartResult {
    data object Started : WearHealthStartResult

    /** Exercise started, but heart-rate callback registration failed and cleanup must be retried via [WearHealthClient.end]. */
    data class StartedWithoutUpdates(val reason: DurationOnlyReason) : WearHealthStartResult

    /** The timer can proceed, but there is no app-owned Health Services exercise or heart rate. */
    data class DurationOnly(val reason: DurationOnlyReason) : WearHealthStartResult
}

enum class DurationOnlyReason {
    OTHER_APP_EXERCISE,
    HEART_RATE_UNAVAILABLE,
    HEALTH_SERVICES_UNAVAILABLE,
    PERMISSION_DENIED,
}

enum class ExistingExercise { NONE, OWNED, OTHER_APP, UNKNOWN }

interface WearHealthClient {
    val updates: Flow<WearHealthUpdate>
    suspend fun start(): WearHealthStartResult
    suspend fun pause()
    suspend fun resume()
    suspend fun end()
    /** Reattaches only when this app already owns an exercise; it must never start a new one. */
    suspend fun reattach(): WearHealthStartResult = WearHealthStartResult.DurationOnly(DurationOnlyReason.HEALTH_SERVICES_UNAVAILABLE)
}

/** Deliberately requests neither GPS nor any distance/calorie data. */
fun createExerciseConfig(): ExerciseConfig = ExerciseConfig.builder(ExerciseType.MEDITATION)
    .setDataTypes(setOf(DataType.HEART_RATE_BPM))
    .setIsAutoPauseAndResumeEnabled(false)
    .setIsGpsEnabled(false)
    .build()

fun startResultFor(
    existingExercise: ExistingExercise,
    heartRateAvailable: Boolean = true,
): WearHealthStartResult = when {
    existingExercise == ExistingExercise.OTHER_APP -> {
        WearHealthStartResult.DurationOnly(DurationOnlyReason.OTHER_APP_EXERCISE)
    }
    existingExercise == ExistingExercise.UNKNOWN -> {
        WearHealthStartResult.DurationOnly(DurationOnlyReason.HEALTH_SERVICES_UNAVAILABLE)
    }
    !heartRateAvailable -> WearHealthStartResult.DurationOnly(DurationOnlyReason.HEART_RATE_UNAVAILABLE)
    else -> WearHealthStartResult.Started
}
