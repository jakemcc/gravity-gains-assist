package com.jakemccrary.gravitygainsassist.health

import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.WeightReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HealthConnectRepositoryTest {
    private val permissionGateway = HealthPermissionGateway()

    @Test
    fun `unavailable SDK short-circuits granted permission checks`() = runTest {
        val manager = FakeHealthConnectManager(
            availability = HealthConnectAvailability.UNAVAILABLE,
        )
        val repository = DefaultHealthConnectRepository(manager, permissionGateway)

        val status = repository.getStatus()

        assertEquals(HealthConnectAvailability.UNAVAILABLE, status.availability)
        assertFalse(status.isWeightPermissionGranted)
        assertFalse(status.isBackgroundReadFeatureAvailable)
        assertFalse(manager.grantedPermissionsRequested)
    }

    @Test
    fun `available SDK maps granted permissions and background feature`() = runTest {
        val manager = FakeHealthConnectManager(
            availability = HealthConnectAvailability.AVAILABLE,
            grantedPermissions = setOf(
                permissionGateway.weightReadPermission,
                permissionGateway.backgroundReadPermission,
            ),
            backgroundReadFeatureAvailable = true,
        )
        val repository = DefaultHealthConnectRepository(manager, permissionGateway)

        val status = repository.getStatus()

        assertTrue(status.isWeightPermissionGranted)
        assertTrue(status.isBackgroundReadFeatureAvailable)
        assertTrue(status.isBackgroundReadPermissionGranted)
    }

    @Test
    fun `read latest weight delegates to selector`() = runTest {
        val first = WeightReading(80.0, Instant.parse("2026-03-01T10:15:30Z"))
        val latest = WeightReading(79.5, Instant.parse("2026-03-03T10:15:30Z"))
        val repository = DefaultHealthConnectRepository(
            healthConnectManager = FakeHealthConnectManager(
                availability = HealthConnectAvailability.AVAILABLE,
                weightReadings = listOf(first, latest),
            ),
            healthPermissionGateway = permissionGateway,
        )

        val reading = repository.readLatestWeight()

        assertEquals(latest, reading)
    }

    private class FakeHealthConnectManager(
        private val availability: HealthConnectAvailability,
        private val grantedPermissions: Set<String> = emptySet(),
        private val backgroundReadFeatureAvailable: Boolean = false,
        private val weightReadings: List<WeightReading> = emptyList(),
    ) : HealthConnectManager {
        var grantedPermissionsRequested: Boolean = false

        override suspend fun getAvailability(): HealthConnectAvailability = availability

        override suspend fun getGrantedPermissions(): Set<String> {
            grantedPermissionsRequested = true
            return grantedPermissions
        }

        override suspend fun isBackgroundReadFeatureAvailable(): Boolean {
            return backgroundReadFeatureAvailable
        }

        override suspend fun readWeightRecords(): List<WeightReading> = weightReadings
    }
}
