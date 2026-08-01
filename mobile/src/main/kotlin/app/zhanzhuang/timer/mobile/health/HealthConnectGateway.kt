package app.zhanzhuang.timer.mobile.health

import app.zhanzhuang.timer.model.SessionRecord

enum class HealthAvailability {
    AVAILABLE,
    MINDFULNESS_UNSUPPORTED,
    UNAVAILABLE,
}

sealed interface HealthWriteResult {
    data class Success(
        val sessionWritten: Boolean,
        val heartRateWritten: Boolean,
    ) : HealthWriteResult

    data class PermissionMissing(val permissions: Set<String>) : HealthWriteResult
    data class Unavailable(val reason: String) : HealthWriteResult
    data class Retryable(val reason: String) : HealthWriteResult
    data class PermanentFailure(val reason: String) : HealthWriteResult
}

interface HealthConnectGateway {
    suspend fun availability(): HealthAvailability
    fun requiredWritePermissions(includeHeartRate: Boolean): Set<String>
    suspend fun write(record: SessionRecord): HealthWriteResult
}
