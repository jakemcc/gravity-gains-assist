package com.jakemccrary.gravitygainsassist.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

interface AppStateStore {
    val appState: Flow<AppState>

    suspend fun update(transform: (AppState) -> AppState)
}

class PreferenceAppStateStore(
    private val dataStore: DataStore<Preferences>,
) : AppStateStore {
    override val appState: Flow<AppState> = dataStore.data.map { preferences ->
        preferences.toAppState()
    }

    override suspend fun update(transform: (AppState) -> AppState) {
        dataStore.edit { preferences ->
            val updatedState = transform(preferences.toAppState())
            preferences.writeAppState(updatedState)
        }
    }

    private fun Preferences.toAppState(): AppState {
        val lastWeightAt = this[LAST_WEIGHT_AT_KEY]

        return AppState(
            lastWeight = if (lastWeightAt == null) {
                null
            } else {
                WeightReading(
                    kilograms = this[LAST_WEIGHT_KG_KEY] ?: 0.0,
                    recordedAt = Instant.ofEpochMilli(lastWeightAt),
                )
            },
            lastSyncAttemptAt = this[LAST_SYNC_ATTEMPT_AT_KEY]?.let(Instant::ofEpochMilli),
            lastSyncSuccessAt = this[LAST_SYNC_SUCCESS_AT_KEY]?.let(Instant::ofEpochMilli),
            lastSyncSkippedReason = this[LAST_SYNC_SKIPPED_REASON_KEY]?.let(SyncSkipReason::valueOf),
            lastSyncFailureMessage = this[LAST_SYNC_FAILURE_MESSAGE_KEY],
            lastSubmittedWeight = this[LAST_SUBMITTED_DATE_KEY]?.let { epochDay ->
                SubmittedWeight(
                    date = LocalDate.ofEpochDay(epochDay),
                    weightLbs = this[LAST_SUBMITTED_WEIGHT_LBS_KEY] ?: 0.0,
                )
            },
        )
    }

    private fun MutablePreferences.writeAppState(appState: AppState) {
        if (appState.lastWeight != null) {
            val weight = appState.lastWeight
            this[LAST_WEIGHT_KG_KEY] = weight.kilograms
            this[LAST_WEIGHT_AT_KEY] = weight.recordedAt.toEpochMilli()
        } else {
            remove(LAST_WEIGHT_KG_KEY)
            remove(LAST_WEIGHT_AT_KEY)
        }

        if (appState.lastSyncAttemptAt != null) {
            this[LAST_SYNC_ATTEMPT_AT_KEY] = appState.lastSyncAttemptAt.toEpochMilli()
        } else {
            remove(LAST_SYNC_ATTEMPT_AT_KEY)
        }

        if (appState.lastSyncSuccessAt != null) {
            this[LAST_SYNC_SUCCESS_AT_KEY] = appState.lastSyncSuccessAt.toEpochMilli()
        } else {
            remove(LAST_SYNC_SUCCESS_AT_KEY)
        }

        if (appState.lastSyncSkippedReason != null) {
            this[LAST_SYNC_SKIPPED_REASON_KEY] = appState.lastSyncSkippedReason.name
        } else {
            remove(LAST_SYNC_SKIPPED_REASON_KEY)
        }

        if (appState.lastSyncFailureMessage != null) {
            this[LAST_SYNC_FAILURE_MESSAGE_KEY] = appState.lastSyncFailureMessage
        } else {
            remove(LAST_SYNC_FAILURE_MESSAGE_KEY)
        }

        if (appState.lastSubmittedWeight != null) {
            this[LAST_SUBMITTED_DATE_KEY] = appState.lastSubmittedWeight.date.toEpochDay()
            this[LAST_SUBMITTED_WEIGHT_LBS_KEY] = appState.lastSubmittedWeight.weightLbs
        } else {
            remove(LAST_SUBMITTED_DATE_KEY)
            remove(LAST_SUBMITTED_WEIGHT_LBS_KEY)
        }
    }

    private companion object {
        val LAST_WEIGHT_KG_KEY = doublePreferencesKey("last_weight_kg")
        val LAST_WEIGHT_AT_KEY = longPreferencesKey("last_weight_at")
        val LAST_SYNC_ATTEMPT_AT_KEY = longPreferencesKey("last_sync_attempt_at")
        val LAST_SYNC_SUCCESS_AT_KEY = longPreferencesKey("last_sync_success_at")
        val LAST_SYNC_SKIPPED_REASON_KEY = stringPreferencesKey("last_sync_skipped_reason")
        val LAST_SYNC_FAILURE_MESSAGE_KEY = stringPreferencesKey("last_sync_failure_message")
        val LAST_SUBMITTED_DATE_KEY = longPreferencesKey("last_submitted_date")
        val LAST_SUBMITTED_WEIGHT_LBS_KEY = doublePreferencesKey("last_submitted_weight_lbs")
    }
}
