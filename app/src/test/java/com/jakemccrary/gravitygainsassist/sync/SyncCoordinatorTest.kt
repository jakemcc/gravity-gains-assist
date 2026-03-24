package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.data.AppStateRepository
import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.SyncOutcome
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.SyncTrigger
import com.jakemccrary.gravitygainsassist.model.WeightReading
import com.jakemccrary.gravitygainsassist.health.HealthConnectRepository
import com.jakemccrary.gravitygainsassist.website.SubmissionResult
import com.jakemccrary.gravitygainsassist.website.WebsiteSubmissionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SyncCoordinatorTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `records a sync attempt and skips when Health Connect is unavailable`() = runTest {
        val appStateRepository = FakeAppStateRepository()
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = unavailableStatus(),
            ),
            appStateRepository = appStateRepository,
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(),
            clock = fixedClock,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(
            SyncOutcome.Skipped(SyncSkipReason.HEALTH_CONNECT_UNAVAILABLE),
            outcome,
        )
        assertEquals(fixedClock.instant(), appStateRepository.recordedSyncAttempts.single())
        assertEquals(SyncSkipReason.HEALTH_CONNECT_UNAVAILABLE, appStateRepository.lastSkippedReason)
    }

    @Test
    fun `background worker skips when background reads are not available`() = runTest {
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(
                    isBackgroundReadFeatureAvailable = false,
                    isBackgroundReadPermissionGranted = false,
                ),
            ),
            appStateRepository = FakeAppStateRepository(),
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(),
            clock = fixedClock,
        )

        val outcome = coordinator.runSync(SyncTrigger.BACKGROUND_WORK)

        assertEquals(
            SyncOutcome.Skipped(SyncSkipReason.BACKGROUND_READ_UNAVAILABLE),
            outcome,
        )
    }

    @Test
    fun `sync skips when there is no weight data`() = runTest {
        val websiteSubmissionRepository = FakeWebsiteSubmissionRepository()
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(),
                latestWeight = null,
            ),
            appStateRepository = FakeAppStateRepository(),
            websiteSubmissionRepository = websiteSubmissionRepository,
            clock = fixedClock,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(
            SyncOutcome.Skipped(SyncSkipReason.NO_WEIGHT_DATA),
            outcome,
        )
        assertTrue(websiteSubmissionRepository.submittedWeights.isEmpty())
    }

    @Test
    fun `successful sync submits weight and stores success state`() = runTest {
        val latestWeight = WeightReading(79.4, Instant.parse("2026-03-22T07:00:00Z"))
        val appStateRepository = FakeAppStateRepository()
        val websiteSubmissionRepository = FakeWebsiteSubmissionRepository()
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(),
                latestWeight = latestWeight,
            ),
            appStateRepository = appStateRepository,
            websiteSubmissionRepository = websiteSubmissionRepository,
            clock = fixedClock,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(SyncOutcome.Succeeded(latestWeight), outcome)
        assertEquals(listOf(latestWeight), websiteSubmissionRepository.submittedWeights)
        assertEquals(
            listOf(appStateRepository.recordedSyncSuccesses.single().second),
            listOf(latestWeight),
        )
        assertEquals(
            SubmittedWeight(LocalDate.parse("2026-03-22"), 175.0),
            appStateRepository.recordedSyncSuccesses.single().third,
        )
    }

    @Test
    fun `missing session becomes a skipped sync and stores session skip state`() = runTest {
        val latestWeight = WeightReading(79.4, Instant.parse("2026-03-22T07:00:00Z"))
        val appStateRepository = FakeAppStateRepository()
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(),
                latestWeight = latestWeight,
            ),
            appStateRepository = appStateRepository,
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(
                result = SubmissionResult.MissingSession,
            ),
            clock = fixedClock,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(SyncOutcome.Skipped(SyncSkipReason.MISSING_SESSION), outcome)
        assertEquals(SyncSkipReason.MISSING_SESSION, appStateRepository.lastSkippedReason)
        assertTrue(appStateRepository.recordedSyncSuccesses.isEmpty())
    }

    @Test
    fun `auth expired becomes invalid session skip`() = runTest {
        val latestWeight = WeightReading(79.4, Instant.parse("2026-03-22T07:00:00Z"))
        val appStateRepository = FakeAppStateRepository()
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(),
                latestWeight = latestWeight,
            ),
            appStateRepository = appStateRepository,
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(
                result = SubmissionResult.AuthExpired,
            ),
            clock = fixedClock,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(SyncOutcome.Skipped(SyncSkipReason.INVALID_SESSION), outcome)
        assertEquals(SyncSkipReason.INVALID_SESSION, appStateRepository.lastSkippedReason)
    }

    @Test
    fun `network failures surface as failed outcomes and store failure message`() = runTest {
        val latestWeight = WeightReading(79.4, Instant.parse("2026-03-22T07:00:00Z"))
        val appStateRepository = FakeAppStateRepository()
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(),
                latestWeight = latestWeight,
            ),
            appStateRepository = appStateRepository,
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(
                result = SubmissionResult.NetworkFailure(java.io.IOException("offline")),
            ),
            clock = fixedClock,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertTrue(outcome is SyncOutcome.Failed)
        assertEquals(
            "Network error while submitting to Grip Gains.",
            appStateRepository.lastFailureMessage,
        )
    }

    @Test
    fun `sync failures surface as failed outcomes`() = runTest {
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(),
                throwable = IllegalStateException("boom"),
            ),
            appStateRepository = FakeAppStateRepository(),
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(),
            clock = fixedClock,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertTrue(outcome is SyncOutcome.Failed)
    }

    private fun availableStatus(
        isBackgroundReadFeatureAvailable: Boolean = true,
        isBackgroundReadPermissionGranted: Boolean = true,
    ): HealthConnectStatus {
        return HealthConnectStatus(
            availability = HealthConnectAvailability.AVAILABLE,
            isWeightPermissionGranted = true,
            isBackgroundReadFeatureAvailable = isBackgroundReadFeatureAvailable,
            isBackgroundReadPermissionGranted = isBackgroundReadPermissionGranted,
        )
    }

    private fun unavailableStatus(): HealthConnectStatus {
        return HealthConnectStatus(
            availability = HealthConnectAvailability.UNAVAILABLE,
            isWeightPermissionGranted = false,
            isBackgroundReadFeatureAvailable = false,
            isBackgroundReadPermissionGranted = false,
        )
    }

    private class FakeHealthConnectRepository(
        private val status: HealthConnectStatus,
        private val latestWeight: WeightReading? = null,
        private val throwable: Throwable? = null,
    ) : HealthConnectRepository {
        override suspend fun getStatus(): HealthConnectStatus = status

        override suspend fun readLatestWeight(): WeightReading? {
            throwable?.let { throw it }
            return latestWeight
        }
    }

    private class FakeWebsiteSubmissionRepository(
        private val result: SubmissionResult = SubmissionResult.Success(
            SubmittedWeight(LocalDate.parse("2026-03-22"), 175.0),
        ),
    ) : WebsiteSubmissionRepository {
        val submittedWeights = mutableListOf<WeightReading>()

        override suspend fun submitWeight(reading: WeightReading): SubmissionResult {
            submittedWeights += reading
            return result
        }
    }

    private class FakeAppStateRepository : AppStateRepository {
        private val backingState = MutableStateFlow(AppState())

        val recordedSyncAttempts = mutableListOf<Instant>()
        val recordedSyncSuccesses = mutableListOf<Triple<Instant, WeightReading?, SubmittedWeight>>()
        var lastSkippedReason: SyncSkipReason? = null
        var lastFailureMessage: String? = null

        override val appState: Flow<AppState> = backingState

        override suspend fun recordLatestWeight(reading: WeightReading?) {
            backingState.value = backingState.value.copy(lastWeight = reading)
        }

        override suspend fun recordSyncAttempt(at: Instant) {
            recordedSyncAttempts += at
            backingState.value = backingState.value.copy(lastSyncAttemptAt = at)
        }

        override suspend fun recordSyncSkipped(reason: SyncSkipReason) {
            lastSkippedReason = reason
            backingState.value = backingState.value.copy(lastSyncSkippedReason = reason)
        }

        override suspend fun recordSyncFailure(message: String) {
            lastFailureMessage = message
            backingState.value = backingState.value.copy(lastSyncFailureMessage = message)
        }

        override suspend fun recordSyncSuccess(
            at: Instant,
            latestWeight: WeightReading?,
            submittedWeight: SubmittedWeight,
        ) {
            recordedSyncSuccesses += Triple(at, latestWeight, submittedWeight)
            backingState.value = backingState.value.copy(
                lastWeight = latestWeight,
                lastSyncSuccessAt = at,
                lastSubmittedWeight = submittedWeight,
            )
        }
    }
}
