package com.jakemccrary.gravitygainsassist.ui

import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.WeightReading
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MainScreenDashboardModelTest {
    @Test
    fun `missing permissions promotes grant permissions action`() {
        val screenState = MainScreenState(
            healthStatus = HealthConnectStatus(
                availability = HealthConnectAvailability.AVAILABLE,
                isWeightPermissionGranted = false,
                isBackgroundReadFeatureAvailable = true,
                isBackgroundReadPermissionGranted = false,
            ),
            sessionState = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT),
            lastWeight = WeightReading(
                kilograms = 67.3,
                recordedAt = Instant.parse("2023-10-24T14:32:00Z"),
            ),
        )

        val dashboardModel = screenState.toDashboardUiModel()

        assertEquals("Permissions needed", dashboardModel.contextLine)
        assertEquals("Grant permissions", dashboardModel.setupAction?.label)
        assertEquals("67.3", dashboardModel.weightValue)
        assertEquals("kg", dashboardModel.weightUnit)
    }

    @Test
    fun `missing sign in promotes sign in action`() {
        val screenState = MainScreenState(
            healthStatus = HealthConnectStatus(
                availability = HealthConnectAvailability.AVAILABLE,
                isWeightPermissionGranted = true,
                isBackgroundReadFeatureAvailable = true,
                isBackgroundReadPermissionGranted = true,
            ),
            sessionState = GripGainsSessionState(GripGainsSessionState.Status.NO_TOKEN),
        )

        val dashboardModel = screenState.toDashboardUiModel()

        assertEquals("Sign in needed", dashboardModel.contextLine)
        assertEquals("Sign in to Grip Gains", dashboardModel.setupAction?.label)
    }

    @Test
    fun `sync failures take over the sync panel`() {
        val screenState = MainScreenState(
            healthStatus = HealthConnectStatus(
                availability = HealthConnectAvailability.AVAILABLE,
                isWeightPermissionGranted = true,
                isBackgroundReadFeatureAvailable = true,
                isBackgroundReadPermissionGranted = true,
            ),
            sessionState = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT),
            lastSyncSuccessAt = Instant.parse("2023-10-25T14:32:00Z"),
            lastSyncFailureMessage = "Grip Gains rejected the submission",
        )

        val dashboardModel = screenState.toDashboardUiModel()

        assertEquals("Sync issue", dashboardModel.syncSummary.title)
        assertEquals("Grip Gains rejected the submission", dashboardModel.syncSummary.detail)
        assertEquals(SyncSummaryTone.ERROR, dashboardModel.syncSummary.tone)
    }

    @Test
    fun `successful state shows calm sync summary and no setup action`() {
        val screenState = MainScreenState(
            healthStatus = HealthConnectStatus(
                availability = HealthConnectAvailability.AVAILABLE,
                isWeightPermissionGranted = true,
                isBackgroundReadFeatureAvailable = true,
                isBackgroundReadPermissionGranted = true,
            ),
            sessionState = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT),
            lastSyncSuccessAt = Instant.parse("2023-10-25T14:32:00Z"),
        )

        val dashboardModel = screenState.toDashboardUiModel()

        assertNull(dashboardModel.contextLine)
        assertNull(dashboardModel.setupAction)
        assertEquals("Last successful sync", dashboardModel.syncSummary.title)
        assertEquals(SyncSummaryTone.SUCCESS, dashboardModel.syncSummary.tone)
    }
}
