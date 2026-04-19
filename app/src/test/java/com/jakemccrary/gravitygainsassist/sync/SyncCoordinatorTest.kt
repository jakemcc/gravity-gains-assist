package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.data.AppStateRepository
import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.SyncFailureKind
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
            zoneId = ZoneOffset.UTC,
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
            zoneId = ZoneOffset.UTC,
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
            zoneId = ZoneOffset.UTC,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(
            SyncOutcome.Skipped(SyncSkipReason.NO_WEIGHT_DATA),
            outcome,
        )
        assertTrue(websiteSubmissionRepository.submittedWeights.isEmpty())
    }

    @Test
    fun `successful sync submits weight stores success state and notifies`() = runTest {
        val latestWeight = WeightReading(79.4, Instant.parse("2026-03-22T07:00:00Z"))
        val appStateRepository = FakeAppStateRepository()
        val websiteSubmissionRepository = FakeWebsiteSubmissionRepository()
        val notifier = FakeSyncFailureNotifier()
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(),
                latestWeight = latestWeight,
            ),
            appStateRepository = appStateRepository,
            websiteSubmissionRepository = websiteSubmissionRepository,
            clock = fixedClock,
            zoneId = ZoneOffset.UTC,
            syncFailureNotifier = notifier,
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
        assertEquals(listOf("Synced weight to Grip Gains."), notifier.successMessages)
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
            zoneId = ZoneOffset.UTC,
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
            zoneId = ZoneOffset.UTC,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(SyncOutcome.Skipped(SyncSkipReason.INVALID_SESSION), outcome)
        assertEquals(SyncSkipReason.INVALID_SESSION, appStateRepository.lastSkippedReason)
    }

    @Test
    fun `first network failures surface as network failed outcomes and schedule one retry message`() = runTest {
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
            zoneId = ZoneOffset.UTC,
            syncFailureNotifier = FakeSyncFailureNotifier(),
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(SyncFailureKind.NETWORK, (outcome as SyncOutcome.Failed).kind)
        assertEquals(
            "Network error while submitting to Grip Gains. Retrying once in 2 minutes.",
            appStateRepository.lastFailureMessage,
        )
    }

    @Test
    fun `retry network failures tell the user to retry manually`() = runTest {
        val latestWeight = WeightReading(79.4, Instant.parse("2026-03-22T07:00:00Z"))
        val appStateRepository = FakeAppStateRepository()
        val notifier = FakeSyncFailureNotifier()
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
            zoneId = ZoneOffset.UTC,
            syncFailureNotifier = notifier,
        )

        val outcome = coordinator.runSync(SyncTrigger.NETWORK_RETRY)

        assertEquals(SyncFailureKind.NETWORK, (outcome as SyncOutcome.Failed).kind)
        assertEquals(
            "Network error while submitting to Grip Gains. Use Run sync now to try again.",
            appStateRepository.lastFailureMessage,
        )
        assertEquals(
            listOf("Network error while submitting to Grip Gains. Use Run sync now to try again."),
            notifier.messages,
        )
    }

    @Test
    fun `server failure with response detail is shown in failure message`() = runTest {
        val latestWeight = WeightReading(79.4, Instant.parse("2026-03-22T07:00:00Z"))
        val appStateRepository = FakeAppStateRepository()
        val notifier = FakeSyncFailureNotifier()
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(),
                latestWeight = latestWeight,
            ),
            appStateRepository = appStateRepository,
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(
                result = SubmissionResult.ServerFailure(
                    statusCode = 400,
                    responseMessage = "A bodyweight entry already exists for this date",
                ),
            ),
            clock = fixedClock,
            zoneId = ZoneOffset.UTC,
            syncFailureNotifier = notifier,
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(SyncFailureKind.SERVER, (outcome as SyncOutcome.Failed).kind)
        assertEquals(
            "Grip Gains rejected the submission: A bodyweight entry already exists for this date",
            appStateRepository.lastFailureMessage,
        )
        assertEquals(
            listOf("Grip Gains rejected the submission: A bodyweight entry already exists for this date"),
            notifier.messages,
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
            zoneId = ZoneOffset.UTC,
            syncFailureNotifier = FakeSyncFailureNotifier(),
        )

        val outcome = coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(SyncFailureKind.UNKNOWN, (outcome as SyncOutcome.Failed).kind)
    }

    @Test
    fun `network failures trigger a sync failure notification`() = runTest {
        val latestWeight = WeightReading(79.4, Instant.parse("2026-03-22T07:00:00Z"))
        val notifier = FakeSyncFailureNotifier()
        val coordinator = DefaultSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                status = availableStatus(),
                latestWeight = latestWeight,
            ),
            appStateRepository = FakeAppStateRepository(),
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(
                result = SubmissionResult.NetworkFailure(java.io.IOException("offline")),
            ),
            clock = fixedClock,
            zoneId = ZoneOffset.UTC,
            syncFailureNotifier = notifier,
        )

        coordinator.runSync(SyncTrigger.MANUAL)

        assertEquals(
            listOf("Network error while submitting to Grip Gains. Retrying once in 2 minutes."),
            notifier.messages,
        )
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

        override suspend fun readLatestWeightFor(date: LocalDate): WeightReading? = latestWeight
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

        override suspend fun setAutoSyncEnabled(enabled: Boolean) {
            backingState.value = backingState.value.copy(autoSyncEnabled = enabled)
        }

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
            preferredNextSyncMinutesOfDay: Int,
        ) {
            recordedSyncSuccesses += Triple(at, latestWeight, submittedWeight)
            backingState.value = backingState.value.copy(
                lastWeight = latestWeight,
                lastSyncSuccessAt = at,
                lastSubmittedWeight = submittedWeight,
                preferredNextSyncMinutesOfDay = preferredNextSyncMinutesOfDay,
            )
        }

        override suspend fun recordHealthConnectStatus(status: HealthConnectStatus) {
            backingState.value = backingState.value.copy(
                weightPermissionGranted = status.isWeightPermissionGranted,
                backgroundReadFeatureAvailable = status.isBackgroundReadFeatureAvailable,
                backgroundReadPermissionGranted = status.isBackgroundReadPermissionGranted,
            )
        }

        override suspend fun recordNextAutoSyncCheck(at: Instant?) {
            backingState.value = backingState.value.copy(nextAutoSyncCheckAt = at)
        }
    }

    private class FakeSyncFailureNotifier : SyncFailureNotifier {
        val messages = mutableListOf<String>()
        val successMessages = mutableListOf<String>()

        override fun notifyFailure(message: String) {
            messages += message
        }

        override fun notifySuccess(message: String) {
            successMessages += message
        }
    }
}
