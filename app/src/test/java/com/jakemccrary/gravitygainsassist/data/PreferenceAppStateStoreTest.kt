package com.jakemccrary.gravitygainsassist.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.WeightReading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class PreferenceAppStateStoreTest {
    @Test
    fun `update clears missing values without crashing`() = runTest {
        val file = File.createTempFile("app-state", ".preferences_pb")
        file.deleteOnExit()
        val store = PreferenceAppStateStore(
            dataStore = PreferenceDataStoreFactory.create(
                produceFile = { file },
            ),
        )
        val initialWeight = WeightReading(82.3, Instant.parse("2026-03-12T07:00:00Z"))
        val attemptAt = Instant.parse("2026-03-12T09:00:00Z")
        val successAt = Instant.parse("2026-03-12T10:00:00Z")

        store.update {
            AppState(
                lastWeight = initialWeight,
                lastSyncAttemptAt = attemptAt,
                lastSyncSuccessAt = successAt,
                lastSyncSkippedReason = SyncSkipReason.MISSING_SESSION,
                lastSyncFailureMessage = "no session",
                lastSubmittedWeight = SubmittedWeight(
                    date = LocalDate.parse("2026-03-12"),
                    weightLbs = 181.4,
                ),
            )
        }

        store.update { AppState() }

        val clearedState = store.appState.first()
        assertNull(clearedState.lastWeight)
        assertNull(clearedState.lastSyncAttemptAt)
        assertNull(clearedState.lastSyncSuccessAt)
        assertNull(clearedState.lastSyncSkippedReason)
        assertNull(clearedState.lastSyncFailureMessage)
        assertNull(clearedState.lastSubmittedWeight)
    }

    @Test
    fun `update persists populated values`() = runTest {
        val file = File.createTempFile("app-state", ".preferences_pb")
        file.deleteOnExit()
        val store = PreferenceAppStateStore(
            dataStore = PreferenceDataStoreFactory.create(
                produceFile = { file },
            ),
        )
        val weight = WeightReading(79.8, Instant.parse("2026-03-13T07:00:00Z"))
        val attemptAt = Instant.parse("2026-03-13T09:00:00Z")
        val successAt = Instant.parse("2026-03-13T10:00:00Z")
        val submittedWeight = SubmittedWeight(
            date = LocalDate.parse("2026-03-13"),
            weightLbs = 175.9,
        )

        store.update {
            AppState(
                lastWeight = weight,
                lastSyncAttemptAt = attemptAt,
                lastSyncSuccessAt = successAt,
                lastSyncSkippedReason = SyncSkipReason.NO_WEIGHT_DATA,
                lastSyncFailureMessage = "no data",
                lastSubmittedWeight = submittedWeight,
            )
        }

        val persistedState = store.appState.first()
        assertEquals(weight, persistedState.lastWeight)
        assertEquals(attemptAt, persistedState.lastSyncAttemptAt)
        assertEquals(successAt, persistedState.lastSyncSuccessAt)
        assertEquals(SyncSkipReason.NO_WEIGHT_DATA, persistedState.lastSyncSkippedReason)
        assertEquals("no data", persistedState.lastSyncFailureMessage)
        assertEquals(submittedWeight, persistedState.lastSubmittedWeight)
    }
}
