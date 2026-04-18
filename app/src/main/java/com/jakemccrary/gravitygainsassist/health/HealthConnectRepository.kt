package com.jakemccrary.gravitygainsassist.health

import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.WeightReading
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

interface HealthConnectRepository {
    suspend fun getStatus(): HealthConnectStatus

    suspend fun readLatestWeight(): WeightReading?

    suspend fun readLatestWeightFor(date: LocalDate): WeightReading?
}

class DefaultHealthConnectRepository(
    private val healthConnectManager: HealthConnectManager,
    private val healthPermissionGateway: HealthPermissionGateway,
    private val zoneId: ZoneId,
    private val clock: Clock = Clock.systemUTC(),
    private val recentWeightLookback: Duration = Duration.ofHours(24),
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
        return LatestWeightSelector.select(
            healthConnectManager.readWeightRecords(clock.instant().minus(recentWeightLookback)),
        )
    }

    override suspend fun readLatestWeightFor(date: LocalDate): WeightReading? {
        return readLatestWeight()?.takeIf { reading ->
            reading.recordedAt.atZone(zoneId).toLocalDate() == date
        }
    }
}

object LatestWeightSelector {
    fun select(readings: List<WeightReading>): WeightReading? {
        return readings.maxByOrNull(WeightReading::recordedAt)
    }
}
