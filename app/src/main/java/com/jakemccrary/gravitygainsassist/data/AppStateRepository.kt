package com.jakemccrary.gravitygainsassist.data

import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.WeightReading
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface AppStateRepository {
    val appState: Flow<AppState>

    suspend fun setAutoSyncEnabled(enabled: Boolean)

    suspend fun recordLatestWeight(reading: WeightReading?)

    suspend fun recordSyncAttempt(at: Instant)

    suspend fun recordSyncSkipped(reason: SyncSkipReason)

    suspend fun recordSyncFailure(message: String)

    suspend fun recordSyncSuccess(
        at: Instant,
        latestWeight: WeightReading?,
        submittedWeight: SubmittedWeight,
        preferredNextSyncMinutesOfDay: Int,
    )

    suspend fun recordHealthConnectStatus(status: HealthConnectStatus)

    suspend fun recordNextAutoSyncCheck(at: Instant?)
}

class DefaultAppStateRepository(
    private val appStateStore: AppStateStore,
) : AppStateRepository {
    override val appState: Flow<AppState> = appStateStore.appState

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        appStateStore.update { currentState ->
            currentState.copy(
                autoSyncEnabled = enabled,
                nextAutoSyncCheckAt = if (enabled) currentState.nextAutoSyncCheckAt else null,
            )
        }
    }

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
        preferredNextSyncMinutesOfDay: Int,
    ) {
        appStateStore.update { currentState ->
            currentState.copy(
                lastWeight = latestWeight ?: currentState.lastWeight,
                lastSyncSuccessAt = at,
                preferredNextSyncMinutesOfDay = preferredNextSyncMinutesOfDay,
                lastSyncSkippedReason = null,
                lastSyncFailureMessage = null,
                lastSubmittedWeight = submittedWeight,
            )
        }
    }

    override suspend fun recordHealthConnectStatus(status: HealthConnectStatus) {
        appStateStore.update { currentState ->
            currentState.copy(
                weightPermissionGranted = status.isWeightPermissionGranted,
                backgroundReadFeatureAvailable = status.isBackgroundReadFeatureAvailable,
                backgroundReadPermissionGranted = status.isBackgroundReadPermissionGranted,
            )
        }
    }

    override suspend fun recordNextAutoSyncCheck(at: Instant?) {
        appStateStore.update { currentState ->
            currentState.copy(nextAutoSyncCheckAt = at)
        }
    }
}
