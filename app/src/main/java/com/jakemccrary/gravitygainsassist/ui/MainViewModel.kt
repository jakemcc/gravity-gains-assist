package com.jakemccrary.gravitygainsassist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jakemccrary.gravitygainsassist.data.AppStateRepository
import com.jakemccrary.gravitygainsassist.health.HealthConnectRepository
import com.jakemccrary.gravitygainsassist.health.HealthPermissionGateway
import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.WeightReading
import com.jakemccrary.gravitygainsassist.sync.SyncScheduler
import com.jakemccrary.gravitygainsassist.website.AuthRepository
import com.jakemccrary.gravitygainsassist.website.GripGainsSession
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

class MainViewModel(
    private val healthConnectRepository: HealthConnectRepository,
    private val appStateRepository: AppStateRepository,
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    private val healthPermissionGateway: HealthPermissionGateway,
    private val autoSyncPolicy: AutoSyncPolicy = AutoSyncPolicy(
        clock = Clock.systemUTC(),
        zoneId = ZoneId.systemDefault(),
    ),
) : ViewModel() {
    private val healthStatus = MutableStateFlow<HealthConnectStatus?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)

    val screenState: StateFlow<MainScreenState> = combine(
        appStateRepository.appState,
        authRepository.sessionState,
        healthStatus,
        statusMessage,
    ) { appState, sessionState, currentHealthStatus, currentMessage ->
        appState.toScreenState(
            sessionState = sessionState,
            healthStatus = currentHealthStatus,
            statusMessage = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainScreenState(),
    )

    fun refresh() {
        viewModelScope.launch {
            runCatching { healthConnectRepository.getStatus() }
                .onSuccess { healthStatus.value = it }
                .onFailure { statusMessage.value = "Unable to read Health Connect status." }
        }
    }

    fun permissionsToRequest(): Set<String> {
        return healthPermissionGateway.permissionsToRequest(
            backgroundReadFeatureAvailable =
                healthStatus.value?.isBackgroundReadFeatureAvailable == true,
        )
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        statusMessage.value = if (grantedPermissions.isEmpty()) {
            "No Health Connect permissions were granted."
        } else {
            "Health Connect permissions updated."
        }
        refresh()
    }

    fun readLatestWeightNow() {
        viewModelScope.launch {
            val currentHealthStatus = runCatching { healthConnectRepository.getStatus() }
                .onFailure { statusMessage.value = "Unable to read Health Connect status." }
                .getOrNull() ?: return@launch

            healthStatus.value = currentHealthStatus
            if (currentHealthStatus.availability != HealthConnectAvailability.AVAILABLE) {
                statusMessage.value = "Health Connect is not available."
                return@launch
            }
            if (!currentHealthStatus.isWeightPermissionGranted) {
                statusMessage.value = "Grant weight permission first."
                return@launch
            }

            val latestWeight = runCatching { healthConnectRepository.readLatestWeight() }
                .onFailure {
                    statusMessage.value = "Reading Health Connect weight failed."
                }
                .getOrNull() ?: return@launch

            appStateRepository.recordLatestWeight(latestWeight)
            val currentAppState = appStateRepository.appState.first()
            val autoSyncQueued = if (autoSyncPolicy.shouldEnqueueSync(currentAppState)) {
                syncScheduler.enqueueImmediateSync()
                true
            } else {
                false
            }
            statusMessage.value = latestWeight.toReadMessage(autoSyncQueued)
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appStateRepository.setAutoSyncEnabled(enabled)
            statusMessage.value = if (enabled) {
                "Auto-sync enabled."
            } else {
                "Auto-sync disabled."
            }
        }
    }

    fun runSyncNow() {
        syncScheduler.enqueueImmediateSync()
        statusMessage.value = "Sync work enqueued."
    }

    fun completeGripGainsSignIn(session: GripGainsSession) {
        viewModelScope.launch {
            authRepository.saveSession(session)
            statusMessage.value = "Grip Gains sign-in saved."
        }
    }

    fun clearGripGainsSession() {
        viewModelScope.launch {
            authRepository.clearSession()
            statusMessage.value = "Grip Gains sign-in cleared."
        }
    }

    class Factory(
        private val healthConnectRepository: HealthConnectRepository,
        private val appStateRepository: AppStateRepository,
        private val authRepository: AuthRepository,
        private val syncScheduler: SyncScheduler,
        private val healthPermissionGateway: HealthPermissionGateway,
        private val autoSyncPolicy: AutoSyncPolicy = AutoSyncPolicy(
            clock = Clock.systemUTC(),
            zoneId = ZoneId.systemDefault(),
        ),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                healthConnectRepository = healthConnectRepository,
                appStateRepository = appStateRepository,
                authRepository = authRepository,
                syncScheduler = syncScheduler,
                healthPermissionGateway = healthPermissionGateway,
                autoSyncPolicy = autoSyncPolicy,
            ) as T
        }
    }
}

data class MainScreenState(
    val autoSyncEnabled: Boolean = false,
    val healthStatus: HealthConnectStatus? = null,
    val sessionState: GripGainsSessionState = GripGainsSessionState(),
    val lastWeight: WeightReading? = null,
    val lastSubmittedWeight: SubmittedWeight? = null,
    val lastSyncAttemptAt: java.time.Instant? = null,
    val lastSyncSuccessAt: java.time.Instant? = null,
    val lastSyncFailureMessage: String? = null,
    val lastSyncSkippedReasonText: String? = null,
    val statusMessage: String? = null,
)

private fun AppState.toScreenState(
    sessionState: GripGainsSessionState,
    healthStatus: HealthConnectStatus?,
    statusMessage: String?,
): MainScreenState {
    return MainScreenState(
        autoSyncEnabled = autoSyncEnabled,
        healthStatus = healthStatus,
        sessionState = sessionState,
        lastWeight = lastWeight,
        lastSubmittedWeight = lastSubmittedWeight,
        lastSyncAttemptAt = lastSyncAttemptAt,
        lastSyncSuccessAt = lastSyncSuccessAt,
        lastSyncFailureMessage = lastSyncFailureMessage,
        lastSyncSkippedReasonText = lastSyncSkippedReason?.displayText(),
        statusMessage = statusMessage,
    )
}

private fun WeightReading?.toReadMessage(autoSyncQueued: Boolean): String {
    return if (this == null) {
        "No weight records found."
    } else {
        val baseMessage = "Read ${"%.1f".format(kilograms)} kg from Health Connect."
        if (autoSyncQueued) {
            "$baseMessage Auto-sync queued."
        } else {
            baseMessage
        }
    }
}

private fun SyncSkipReason.displayText(): String {
    return when (this) {
        SyncSkipReason.HEALTH_CONNECT_UNAVAILABLE -> "Health Connect unavailable"
        SyncSkipReason.WEIGHT_PERMISSION_MISSING -> "Weight permission missing"
        SyncSkipReason.BACKGROUND_READ_UNAVAILABLE -> "Background read unavailable"
        SyncSkipReason.NO_WEIGHT_DATA -> "No weight data"
        SyncSkipReason.MISSING_SESSION -> "No Grip Gains sign-in saved"
        SyncSkipReason.INVALID_SESSION -> "Grip Gains session is invalid"
    }
}
