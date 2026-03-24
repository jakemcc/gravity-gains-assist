package com.jakemccrary.gravitygainsassist.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord

class HealthPermissionGateway {
    val weightReadPermission: String = HealthPermission.getReadPermission(WeightRecord::class)
    val backgroundReadPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    fun permissionsToRequest(backgroundReadFeatureAvailable: Boolean): Set<String> {
        return buildSet {
            add(weightReadPermission)
            if (backgroundReadFeatureAvailable) {
                add(backgroundReadPermission)
            }
        }
    }

    fun isWeightReadGranted(grantedPermissions: Set<String>): Boolean {
        return grantedPermissions.contains(weightReadPermission)
    }

    fun isBackgroundReadGranted(grantedPermissions: Set<String>): Boolean {
        return grantedPermissions.contains(backgroundReadPermission)
    }
}
