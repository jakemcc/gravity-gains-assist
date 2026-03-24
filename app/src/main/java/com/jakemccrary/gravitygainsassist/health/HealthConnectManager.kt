package com.jakemccrary.gravitygainsassist.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.HealthConnectFeatures
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.WeightReading
import java.time.Clock
import java.time.Instant

interface HealthConnectManager {
    suspend fun getAvailability(): HealthConnectAvailability

    suspend fun getGrantedPermissions(): Set<String>

    suspend fun isBackgroundReadFeatureAvailable(): Boolean

    suspend fun readWeightRecords(): List<WeightReading>
}

class AndroidHealthConnectManager(
    private val context: Context,
    private val clock: Clock,
) : HealthConnectManager {
    private val client: HealthConnectClient by lazy(LazyThreadSafetyMode.NONE) {
        HealthConnectClient.getOrCreate(context)
    }

    override suspend fun getAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED

            else -> HealthConnectAvailability.UNAVAILABLE
        }
    }

    override suspend fun getGrantedPermissions(): Set<String> {
        return client.permissionController.getGrantedPermissions()
    }

    override suspend fun isBackgroundReadFeatureAvailable(): Boolean {
        return client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    override suspend fun readWeightRecords(): List<WeightReading> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(Instant.EPOCH, clock.instant()),
            ),
        )

        return response.records.map { record ->
            WeightReading(
                kilograms = record.weight.inKilograms,
                recordedAt = record.time,
            )
        }
    }

    private companion object {
        const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
    }
}
