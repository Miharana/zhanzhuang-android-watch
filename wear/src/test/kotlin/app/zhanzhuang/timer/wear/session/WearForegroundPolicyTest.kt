package app.zhanzhuang.timer.wear.session

import android.content.pm.ServiceInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class WearForegroundPolicyTest {
    @Test fun missingPermissionUsesSpecialUseOnly() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            WearForegroundPolicy.serviceTypeMask(hasHeartRatePermission = false),
        )
    }

    @Test fun grantedPermissionAddsHealthType() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            WearForegroundPolicy.serviceTypeMask(hasHeartRatePermission = true),
        )
    }
}
