package app.zhanzhuang.timer.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPermissionPolicyTest {
    @Test fun requests_permission_only_for_ungranted_android_13_or_newer() {
        assertFalse(shouldRequestNotificationPermission(apiLevel = 32, permissionGranted = false))
        assertFalse(shouldRequestNotificationPermission(apiLevel = 33, permissionGranted = true))
        assertTrue(shouldRequestNotificationPermission(apiLevel = 33, permissionGranted = false))
        assertTrue(shouldRequestNotificationPermission(apiLevel = 37, permissionGranted = false))
    }
}
