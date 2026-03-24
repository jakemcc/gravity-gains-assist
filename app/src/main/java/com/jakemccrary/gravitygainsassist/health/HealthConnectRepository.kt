package com.jakemccrary.gravitygainsassist.health

import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.WeightReading

interface HealthConnectRepository {
    suspend fun getStatus(): HealthConnectStatus

    suspend fun readLatestWeight(): WeightReading?
}

class DefaultHealthConnectRepository(
    private val healthConnectManager: HealthConnectManager,
    private val healthPermissionGateway: HealthPermissionGateway,
) : HealthConnectRepository {
    override suspend fun getStatus(): HealthConnectStatus {
        val availability = healthConnectManager.getAvailability()
        if (availability != HealthConnectAvailability.AVAILABLE) {
            return HealthConnectStatus(
                availability = availability,
                isWeightPermissionGranted = false,
                isBackgroundReadFeatureAvailable = false,
                isBackgroundReadPermissionGranted = false,
            )
        }

        val grantedPermissions = healthConnectManager.getGrantedPermissions()
        val backgroundReadFeatureAvailable = healthConnectManager.isBackgroundReadFeatureAvailable()

        return HealthConnectStatus(
            availability = availability,
            isWeightPermissionGranted =
                healthPermissionGateway.isWeightReadGranted(grantedPermissions),
            isBackgroundReadFeatureAvailable = backgroundReadFeatureAvailable,
            isBackgroundReadPermissionGranted =
                backgroundReadFeatureAvailable &&
                    healthPermissionGateway.isBackgroundReadGranted(grantedPermissions),
        )
    }

    override suspend fun readLatestWeight(): WeightReading? {
        return LatestWeightSelector.select(healthConnectManager.readWeightRecords())
    }
}

object LatestWeightSelector {
    fun select(readings: List<WeightReading>): WeightReading? {
        return readings.maxByOrNull(WeightReading::recordedAt)
    }
}
