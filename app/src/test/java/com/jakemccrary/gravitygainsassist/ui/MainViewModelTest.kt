package com.jakemccrary.gravitygainsassist.ui

import com.jakemccrary.gravitygainsassist.data.AppStateRepository
import com.jakemccrary.gravitygainsassist.health.HealthConnectRepository
import com.jakemccrary.gravitygainsassist.health.HealthPermissionGateway
import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.WeightReading
import com.jakemccrary.gravitygainsassist.sync.SyncScheduler
import com.jakemccrary.gravitygainsassist.website.AuthRepository
import com.jakemccrary.gravitygainsassist.website.GripGainsSession
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reading a weight enqueues auto sync when enabled and today has not synced`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scheduler = FakeSyncScheduler()
        val appStateRepository = FakeAppStateRepository(
            AppState(autoSyncEnabled = true),
        )
        val viewModel = MainViewModel(
            healthConnectRepository = FakeHealthConnectRepository(
                latestWeight = WeightReading(
                    kilograms = 67.3,
                    recordedAt = Instant.parse("2026-03-25T07:00:00Z"),
                ),
            ),
            appStateRepository = appStateRepository,
            authRepository = FakeAuthRepository(),
            syncScheduler = scheduler,
            healthPermissionGateway = HealthPermissionGateway(),
            autoSyncPolicy = AutoSyncPolicy(
                clock = Clock.fixed(Instant.parse("2026-03-25T12:00:00Z"), ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
            ),
        )

        viewModel.readLatestWeightNow()
        advanceUntilIdle()

        assertEquals(1, scheduler.immediateSyncRequests)
    }

    @Test
    fun `reading a weight does not enqueue auto sync when today already synced`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scheduler = FakeSyncScheduler()
        val appStateRepository = FakeAppStateRepository(
            AppState(
                autoSyncEnabled = true,
                lastSyncSuccessAt = Instant.parse("2026-03-25T02:00:00Z"),
            ),
        )
        val viewModel = MainViewModel(
            healthConnectRepository = FakeHealthConnectRepository(
                latestWeight = WeightReading(
                    kilograms = 67.3,
                    recordedAt = Instant.parse("2026-03-25T07:00:00Z"),
                ),
            ),
            appStateRepository = appStateRepository,
            authRepository = FakeAuthRepository(),
            syncScheduler = scheduler,
            healthPermissionGateway = HealthPermissionGateway(),
            autoSyncPolicy = AutoSyncPolicy(
                clock = Clock.fixed(Instant.parse("2026-03-25T12:00:00Z"), ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
            ),
        )

        viewModel.readLatestWeightNow()
        advanceUntilIdle()

        assertEquals(0, scheduler.immediateSyncRequests)
    }

    private class FakeHealthConnectRepository(
        private val latestWeight: WeightReading?,
    ) : HealthConnectRepository {
        override suspend fun getStatus(): HealthConnectStatus {
            return HealthConnectStatus(
                availability = HealthConnectAvailability.AVAILABLE,
                isWeightPermissionGranted = true,
                isBackgroundReadFeatureAvailable = true,
                isBackgroundReadPermissionGranted = true,
            )
        }

        override suspend fun readLatestWeight(): WeightReading? = latestWeight
    }

    private class FakeAppStateRepository(
        initialState: AppState,
    ) : AppStateRepository {
        private val state = MutableStateFlow(initialState)

        override val appState: Flow<AppState> = state

        override suspend fun setAutoSyncEnabled(enabled: Boolean) {
            state.value = state.value.copy(autoSyncEnabled = enabled)
        }

        override suspend fun recordLatestWeight(reading: WeightReading?) {
            state.value = state.value.copy(lastWeight = reading)
        }

        override suspend fun recordSyncAttempt(at: Instant) {
            state.value = state.value.copy(lastSyncAttemptAt = at)
        }

        override suspend fun recordSyncSkipped(reason: com.jakemccrary.gravitygainsassist.model.SyncSkipReason) {
            state.value = state.value.copy(lastSyncSkippedReason = reason)
        }

        override suspend fun recordSyncFailure(message: String) {
            state.value = state.value.copy(lastSyncFailureMessage = message)
        }

        override suspend fun recordSyncSuccess(
            at: Instant,
            latestWeight: WeightReading?,
            submittedWeight: SubmittedWeight,
        ) {
            state.value = state.value.copy(
                lastSyncSuccessAt = at,
                lastWeight = latestWeight,
                lastSubmittedWeight = submittedWeight,
            )
        }
    }

    private class FakeAuthRepository : AuthRepository {
        override val sessionState: StateFlow<GripGainsSessionState> =
            MutableStateFlow(GripGainsSessionState())

        override fun getSessionState(): GripGainsSessionState = sessionState.value

        override fun getActiveSession(): GripGainsSession? = null

        override suspend fun saveSession(session: GripGainsSession) = Unit

        override suspend fun saveToken(token: String) = Unit

        override suspend fun clearSession() = Unit

        override suspend fun markSessionInvalid() = Unit

        override suspend fun markSessionValid() = Unit
    }

    private class FakeSyncScheduler : SyncScheduler {
        var immediateSyncRequests: Int = 0

        override fun scheduleDailySync() = Unit

        override fun enqueueImmediateSync() {
            immediateSyncRequests += 1
        }
    }
}
