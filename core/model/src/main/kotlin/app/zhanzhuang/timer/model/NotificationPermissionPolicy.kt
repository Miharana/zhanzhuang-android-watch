package app.zhanzhuang.timer.model

/** Android 13 introduced the runtime notification permission. */
fun shouldRequestNotificationPermission(apiLevel: Int, permissionGranted: Boolean): Boolean =
    apiLevel >= 33 && !permissionGranted
