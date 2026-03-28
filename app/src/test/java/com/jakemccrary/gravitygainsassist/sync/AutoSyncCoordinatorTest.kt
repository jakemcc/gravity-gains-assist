package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.data.AppStateRepository
import com.jakemccrary.gravitygainsassist.health.HealthConnectRepository
import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.WeightReading
import com.jakemccrary.gravitygainsassist.website.AuthRepository
import com.jakemccrary.gravitygainsassist.website.GripGainsSession
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import com.jakemccrary.gravitygainsassist.website.SubmissionResult
import com.jakemccrary.gravitygainsassist.website.WebsiteSubmissionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AutoSyncCoordinatorTest {
    private val zoneId: ZoneId = ZoneId.of("America/Chicago")
    private val fixedClock = Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC)
    private val availableHealthStatus = HealthConnectStatus(
        availability = HealthConnectAvailability.AVAILABLE,
        isWeightPermissionGranted = true,
        isBackgroundReadFeatureAvailable = true,
        isBackgroundReadPermissionGranted = true,
    )

    @Test
    fun `already synced today does not submit again and schedules tomorrow`() = runTest {
        val appStateRepository = FakeAppStateRepository(
            AppState(
                autoSyncEnabled = true,
                lastSubmittedWeight = SubmittedWeight(
                    date = LocalDate.parse("2026-03-27"),
                    weightLbs = 181.2,
                ),
                preferredNextSyncMinutesOfDay = (7 * 60) + 12,
            ),
        )
        val websiteSubmissionRepository = FakeWebsiteSubmissionRepository()
        val scheduler = FakeSyncScheduler()
        val coordinator = DefaultAutoSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(healthStatus = availableHealthStatus),
            appStateRepository = appStateRepository,
            authRepository = FakeAuthRepository(GripGainsSessionState.Status.TOKEN_PRESENT),
            websiteSubmissionRepository = websiteSubmissionRepository,
            syncScheduler = scheduler,
            autoSyncPlanner = AutoSyncPlanner(fixedClock, zoneId),
            clock = fixedClock,
            zoneId = zoneId,
        )

        coordinator.runAutoSync()

        assertTrue(websiteSubmissionRepository.submittedWeights.isEmpty())
        assertEquals(listOf(Instant.parse("2026-03-28T11:42:00Z")), scheduler.followUpAutoSyncRequests)
        assertTrue(appStateRepository.recordedSyncSuccesses.isEmpty())
    }

    @Test
    fun `no weight found schedules a retry and records no weight state`() = runTest {
        val appStateRepository = FakeAppStateRepository(AppState(autoSyncEnabled = true))
        val scheduler = FakeSyncScheduler()
        val coordinator = DefaultAutoSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                healthStatus = availableHealthStatus,
                todayWeight = null,
            ),
            appStateRepository = appStateRepository,
            authRepository = FakeAuthRepository(GripGainsSessionState.Status.TOKEN_PRESENT),
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(),
            syncScheduler = scheduler,
            autoSyncPlanner = AutoSyncPlanner(fixedClock, zoneId),
            clock = fixedClock,
            zoneId = zoneId,
        )

        coordinator.runAutoSync()

        assertEquals(SyncSkipReason.NO_WEIGHT_DATA, appStateRepository.lastSkippedReason)
        assertEquals(listOf(Instant.parse("2026-03-27T13:30:00Z")), scheduler.followUpAutoSyncRequests)
    }

    @Test
    fun `weight found submits marks success and schedules tomorrow using the weight recorded time anchor`() = runTest {
        val appStateRepository = FakeAppStateRepository(AppState(autoSyncEnabled = true))
        val scheduler = FakeSyncScheduler()
        val todayWeight = WeightReading(
            kilograms = 82.4,
            recordedAt = Instant.parse("2026-03-27T12:00:00Z"),
        )
        val websiteSubmissionRepository = FakeWebsiteSubmissionRepository()
        val coordinator = DefaultAutoSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(
                healthStatus = availableHealthStatus,
                todayWeight = todayWeight,
            ),
            appStateRepository = appStateRepository,
            authRepository = FakeAuthRepository(GripGainsSessionState.Status.TOKEN_PRESENT),
            websiteSubmissionRepository = websiteSubmissionRepository,
            syncScheduler = scheduler,
            autoSyncPlanner = AutoSyncPlanner(fixedClock, zoneId),
            clock = fixedClock,
            zoneId = zoneId,
        )

        coordinator.runAutoSync()

        assertEquals(listOf(todayWeight), websiteSubmissionRepository.submittedWeights)
        assertEquals((7 * 60), appStateRepository.state.value.preferredNextSyncMinutesOfDay)
        assertEquals(
            listOf(Instant.parse("2026-03-28T11:30:00Z")),
            scheduler.followUpAutoSyncRequests,
        )
        assertEquals(LocalDate.parse("2026-03-27"), appStateRepository.state.value.lastSubmittedWeight?.date)
    }

    @Test
    fun `missing session avoids reading weight aggressively and schedules tomorrow`() = runTest {
        val appStateRepository = FakeAppStateRepository(AppState(autoSyncEnabled = true))
        val healthConnectRepository = FakeHealthConnectRepository(healthStatus = availableHealthStatus)
        val scheduler = FakeSyncScheduler()
        val coordinator = DefaultAutoSyncCoordinator(
            healthConnectRepository = healthConnectRepository,
            appStateRepository = appStateRepository,
            authRepository = FakeAuthRepository(GripGainsSessionState.Status.NO_TOKEN),
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(),
            syncScheduler = scheduler,
            autoSyncPlanner = AutoSyncPlanner(fixedClock, zoneId),
            clock = fixedClock,
            zoneId = zoneId,
        )

        coordinator.runAutoSync()

        assertEquals(SyncSkipReason.MISSING_SESSION, appStateRepository.lastSkippedReason)
        assertEquals(0, healthConnectRepository.readTodayWeightCalls)
        assertEquals(listOf(Instant.parse("2026-03-28T12:00:00Z")), scheduler.followUpAutoSyncRequests)
    }

    @Test
    fun `disabling auto sync cancels scheduled work and clears the next check`() = runTest {
        val appStateRepository = FakeAppStateRepository(
            AppState(
                autoSyncEnabled = true,
                nextAutoSyncCheckAt = Instant.parse("2026-03-28T11:42:00Z"),
            ),
        )
        val scheduler = FakeSyncScheduler()
        val coordinator = DefaultAutoSyncCoordinator(
            healthConnectRepository = FakeHealthConnectRepository(healthStatus = availableHealthStatus),
            appStateRepository = appStateRepository,
            authRepository = FakeAuthRepository(GripGainsSessionState.Status.TOKEN_PRESENT),
            websiteSubmissionRepository = FakeWebsiteSubmissionRepository(),
            syncScheduler = scheduler,
            autoSyncPlanner = AutoSyncPlanner(fixedClock, zoneId),
            clock = fixedClock,
            zoneId = zoneId,
        )

        coordinator.setEnabled(false)

        assertEquals(1, scheduler.cancelAutoSyncCalls)
        assertEquals(false, appStateRepository.state.value.autoSyncEnabled)
        assertNull(appStateRepository.state.value.nextAutoSyncCheckAt)
    }

    private class FakeHealthConnectRepository(
        private val healthStatus: HealthConnectStatus,
        private val todayWeight: WeightReading? = null,
    ) : HealthConnectRepository {
        var readTodayWeightCalls: Int = 0

        override suspend fun getStatus(): HealthConnectStatus = healthStatus

        override suspend fun readLatestWeight(): WeightReading? = todayWeight

        override suspend fun readLatestWeightFor(date: LocalDate): WeightReading? {
            readTodayWeightCalls += 1
            return todayWeight
        }
    }

    private class FakeAppStateRepository(
        initialState: AppState,
    ) : AppStateRepository {
        val state = MutableStateFlow(initialState)
        val recordedSyncSuccesses = mutableListOf<Triple<Instant, WeightReading?, SubmittedWeight>>()
        var lastSkippedReason: SyncSkipReason? = null

        override val appState: Flow<AppState> = state

        override suspend fun setAutoSyncEnabled(enabled: Boolean) {
            state.value = state.value.copy(
                autoSyncEnabled = enabled,
                nextAutoSyncCheckAt = if (enabled) state.value.nextAutoSyncCheckAt else null,
            )
        }

        override suspend fun recordLatestWeight(reading: WeightReading?) {
            state.value = state.value.copy(lastWeight = reading)
        }

        override suspend fun recordSyncAttempt(at: Instant) {
            state.value = state.value.copy(lastSyncAttemptAt = at)
        }

        override suspend fun recordSyncSkipped(reason: SyncSkipReason) {
            lastSkippedReason = reason
            state.value = state.value.copy(lastSyncSkippedReason = reason)
        }

        override suspend fun recordSyncFailure(message: String) {
            state.value = state.value.copy(lastSyncFailureMessage = message)
        }

        override suspend fun recordSyncSuccess(
            at: Instant,
            latestWeight: WeightReading?,
            submittedWeight: SubmittedWeight,
            preferredNextSyncMinutesOfDay: Int,
        ) {
            recordedSyncSuccesses += Triple(at, latestWeight, submittedWeight)
            state.value = state.value.copy(
                lastSyncSuccessAt = at,
                lastWeight = latestWeight,
                lastSubmittedWeight = submittedWeight,
                preferredNextSyncMinutesOfDay = preferredNextSyncMinutesOfDay,
            )
        }

        override suspend fun recordHealthConnectStatus(status: HealthConnectStatus) {
            state.value = state.value.copy(
                weightPermissionGranted = status.isWeightPermissionGranted,
                backgroundReadFeatureAvailable = status.isBackgroundReadFeatureAvailable,
                backgroundReadPermissionGranted = status.isBackgroundReadPermissionGranted,
            )
        }

        override suspend fun recordNextAutoSyncCheck(at: Instant?) {
            state.value = state.value.copy(nextAutoSyncCheckAt = at)
        }
    }

    private class FakeAuthRepository(
        status: GripGainsSessionState.Status,
    ) : AuthRepository {
        private val mutableState = MutableStateFlow(GripGainsSessionState(status))

        override val sessionState: StateFlow<GripGainsSessionState> = mutableState

        override fun getSessionState(): GripGainsSessionState = mutableState.value

        override fun getActiveSession(): GripGainsSession? = null

        override suspend fun saveSession(session: GripGainsSession) = Unit

        override suspend fun saveToken(token: String) = Unit

        override suspend fun clearSession() = Unit

        override suspend fun markSessionInvalid() = Unit

        override suspend fun markSessionValid() = Unit
    }

    private class FakeWebsiteSubmissionRepository(
        private val result: SubmissionResult = SubmissionResult.Success(
            SubmittedWeight(LocalDate.parse("2026-03-27"), 181.7),
        ),
    ) : WebsiteSubmissionRepository {
        val submittedWeights = mutableListOf<WeightReading>()

        override suspend fun submitWeight(reading: WeightReading): SubmissionResult {
            submittedWeights += reading
            return result
        }
    }

    private class FakeSyncScheduler : SyncScheduler {
        val followUpAutoSyncRequests = mutableListOf<Instant>()
        var cancelAutoSyncCalls: Int = 0

        override fun enqueueImmediateSync() = Unit

        override fun replaceAutoSync(at: Instant) = Unit

        override fun scheduleNextAutoSync(at: Instant) {
            followUpAutoSyncRequests += at
        }

        override fun cancelAutoSync() {
            cancelAutoSyncCalls += 1
        }
    }
}
