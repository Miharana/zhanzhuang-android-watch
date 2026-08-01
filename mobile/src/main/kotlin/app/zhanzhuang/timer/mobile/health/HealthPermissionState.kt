package app.zhanzhuang.timer.mobile.health

sealed interface HealthPermissionState {
    data object Granted : HealthPermissionState
    data class Missing(val permissions: Set<String>) : HealthPermissionState
}
