package com.jakemccrary.gravitygainsassist.data

import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AppStateRepositoryTest {
    @Test
    fun `record latest weight replaces the stored weight`() = runTest {
        val repository = DefaultAppStateRepository(FakeAppStateStore())
        val weight = WeightReading(80.2, Instant.parse("2026-03-10T07:00:00Z"))

        repository.recordLatestWeight(weight)

        assertEquals(weight, repository.appState.first().lastWeight)
    }

    @Test
    fun `record latest weight with null clears the stored weight`() = runTest {
        val store = FakeAppStateStore(
            initialState = AppState(
                lastWeight = WeightReading(90.0, Instant.parse("2026-03-09T07:00:00Z")),
            ),
        )
        val repository = DefaultAppStateRepository(store)

        repository.recordLatestWeight(null)

        assertNull(repository.appState.first().lastWeight)
    }

    @Test
    fun `record sync attempt only updates the attempt timestamp`() = runTest {
        val initialWeight = WeightReading(90.0, Instant.parse("2026-03-09T07:00:00Z"))
        val repository = DefaultAppStateRepository(
            FakeAppStateStore(initialState = AppState(lastWeight = initialWeight)),
        )
        val attemptAt = Instant.parse("2026-03-10T09:00:00Z")

        repository.recordSyncAttempt(attemptAt)

        val state = repository.appState.first()
        assertEquals(initialWeight, state.lastWeight)
        assertEquals(attemptAt, state.lastSyncAttemptAt)
    }

    @Test
    fun `record sync success updates success time and latest weight`() = runTest {
        val repository = DefaultAppStateRepository(FakeAppStateStore())
        val latestWeight = WeightReading(81.1, Instant.parse("2026-03-11T07:00:00Z"))
        val successAt = Instant.parse("2026-03-11T09:00:00Z")
        val submittedWeight = SubmittedWeight(
            date = LocalDate.parse("2026-03-11"),
            weightLbs = 178.8,
        )

        repository.recordSyncSuccess(
            at = successAt,
            latestWeight = latestWeight,
            submittedWeight = submittedWeight,
            preferredNextSyncMinutesOfDay = (4 * 60),
        )

        val state = repository.appState.first()
        assertEquals(latestWeight, state.lastWeight)
        assertEquals(successAt, state.lastSyncSuccessAt)
        assertEquals(submittedWeight, state.lastSubmittedWeight)
        assertEquals((4 * 60), state.preferredNextSyncMinutesOfDay)
    }

    @Test
    fun `record sync skipped clears failure and stores skip reason`() = runTest {
        val repository = DefaultAppStateRepository(
            FakeAppStateStore(
                initialState = AppState(lastSyncFailureMessage = "Old failure"),
            ),
        )

        repository.recordSyncSkipped(SyncSkipReason.MISSING_SESSION)

        val state = repository.appState.first()
        assertEquals(SyncSkipReason.MISSING_SESSION, state.lastSyncSkippedReason)
        assertNull(state.lastSyncFailureMessage)
    }

    @Test
    fun `record sync failure clears skip state and stores message`() = runTest {
        val repository = DefaultAppStateRepository(
            FakeAppStateStore(
                initialState = AppState(lastSyncSkippedReason = SyncSkipReason.NO_WEIGHT_DATA),
            ),
        )

        repository.recordSyncFailure("Network error")

        val state = repository.appState.first()
        assertEquals("Network error", state.lastSyncFailureMessage)
        assertNull(state.lastSyncSkippedReason)
    }

    @Test
    fun `record next auto sync check updates persisted scheduling state`() = runTest {
        val repository = DefaultAppStateRepository(FakeAppStateStore())
        val nextCheckAt = Instant.parse("2026-03-12T11:42:00Z")

        repository.recordNextAutoSyncCheck(nextCheckAt)

        assertEquals(nextCheckAt, repository.appState.first().nextAutoSyncCheckAt)
    }

    @Test
    fun `disabling auto sync clears any stored next auto sync check`() = runTest {
        val repository = DefaultAppStateRepository(
            FakeAppStateStore(
                initialState = AppState(
                    autoSyncEnabled = true,
                    nextAutoSyncCheckAt = Instant.parse("2026-03-12T11:42:00Z"),
                ),
            ),
        )

        repository.setAutoSyncEnabled(false)

        val state = repository.appState.first()
        assertEquals(false, state.autoSyncEnabled)
        assertNull(state.nextAutoSyncCheckAt)
    }

    private class FakeAppStateStore(
        initialState: AppState = AppState(),
    ) : AppStateStore {
        private val backingState = MutableStateFlow(initialState)

        override val appState: Flow<AppState> = backingState

        override suspend fun update(transform: (AppState) -> AppState) {
            backingState.value = transform(backingState.value)
        }
    }
}
