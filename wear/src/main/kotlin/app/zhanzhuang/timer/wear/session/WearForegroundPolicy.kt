package app.zhanzhuang.timer.wear.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat

/** Keeps the declared foreground type aligned with the sensor work the service may perform. */
object WearForegroundPolicy {
    fun hasHeartRatePermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 36) {
            "android.permission.health.READ_HEART_RATE"
        } else {
            Manifest.permission.BODY_SENSORS
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun serviceTypeMask(hasHeartRatePermission: Boolean): Int =
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
            if (hasHeartRatePermission) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
}
