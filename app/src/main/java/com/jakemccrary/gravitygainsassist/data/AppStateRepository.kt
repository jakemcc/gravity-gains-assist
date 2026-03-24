package com.jakemccrary.gravitygainsassist.data

import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.WeightReading
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface AppStateRepository {
    val appState: Flow<AppState>

    suspend fun recordLatestWeight(reading: WeightReading?)

    suspend fun recordSyncAttempt(at: Instant)

    suspend fun recordSyncSkipped(reason: SyncSkipReason)

    suspend fun recordSyncFailure(message: String)

    suspend fun recordSyncSuccess(
        at: Instant,
        latestWeight: WeightReading?,
        submittedWeight: SubmittedWeight,
    )
}

class DefaultAppStateRepository(
    private val appStateStore: AppStateStore,
) : AppStateRepository {
    override val appState: Flow<AppState> = appStateStore.appState

    override suspend fun recordLatestWeight(reading: WeightReading?) {
        appStateStore.update { currentState ->
            currentState.copy(lastWeight = reading)
        }
    }

    override suspend fun recordSyncAttempt(at: Instant) {
        appStateStore.update { currentState ->
            currentState.copy(
                lastSyncAttemptAt = at,
                lastSyncSkippedReason = null,
                lastSyncFailureMessage = null,
            )
        }
    }

    override suspend fun recordSyncSkipped(reason: SyncSkipReason) {
        appStateStore.update { currentState ->
            currentState.copy(
                lastSyncSkippedReason = reason,
                lastSyncFailureMessage = null,
            )
        }
    }

    override suspend fun recordSyncFailure(message: String) {
        appStateStore.update { currentState ->
            currentState.copy(
                lastSyncSkippedReason = null,
                lastSyncFailureMessage = message,
            )
        }
    }

    override suspend fun recordSyncSuccess(
        at: Instant,
        latestWeight: WeightReading?,
        submittedWeight: SubmittedWeight,
    ) {
        appStateStore.update { currentState ->
            currentState.copy(
                lastWeight = latestWeight ?: currentState.lastWeight,
                lastSyncSuccessAt = at,
                lastSyncSkippedReason = null,
                lastSyncFailureMessage = null,
                lastSubmittedWeight = submittedWeight,
            )
        }
    }
}
