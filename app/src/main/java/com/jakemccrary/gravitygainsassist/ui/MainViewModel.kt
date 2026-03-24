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
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val healthConnectRepository: HealthConnectRepository,
    private val appStateRepository: AppStateRepository,
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    private val healthPermissionGateway: HealthPermissionGateway,
) : ViewModel() {
    private val healthStatus = MutableStateFlow<HealthConnectStatus?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val tokenDraft = MutableStateFlow("")

    val screenState: StateFlow<MainScreenState> = combine(
        appStateRepository.appState,
        authRepository.sessionState,
        healthStatus,
        statusMessage,
        tokenDraft,
    ) { appState, sessionState, currentHealthStatus, currentMessage, currentTokenDraft ->
        appState.toScreenState(
            sessionState = sessionState,
            healthStatus = currentHealthStatus,
            statusMessage = currentMessage,
            tokenDraft = currentTokenDraft,
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
            statusMessage.value = latestWeight.toReadMessage()
        }
    }

    fun scheduleDailySync() {
        syncScheduler.scheduleDailySync()
        statusMessage.value = if (healthStatus.value?.isBackgroundReadFeatureAvailable == true) {
            "Daily sync scheduled."
        } else {
            "Daily sync scheduled. Background reads will wait for platform support."
        }
    }

    fun runSyncNow() {
        syncScheduler.enqueueImmediateSync()
        statusMessage.value = "Sync work enqueued."
    }

    fun onTokenChanged(value: String) {
        tokenDraft.value = value
    }

    fun saveToken() {
        viewModelScope.launch {
            val normalizedToken = tokenDraft.value.trim()
            if (normalizedToken.isBlank()) {
                statusMessage.value = "Enter a Grip Gains token first."
                return@launch
            }

            authRepository.saveToken(normalizedToken)
            tokenDraft.value = ""
            statusMessage.value = "Grip Gains token saved."
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            authRepository.clearSession()
            tokenDraft.value = ""
            statusMessage.value = "Grip Gains token cleared."
        }
    }

    class Factory(
        private val healthConnectRepository: HealthConnectRepository,
        private val appStateRepository: AppStateRepository,
        private val authRepository: AuthRepository,
        private val syncScheduler: SyncScheduler,
        private val healthPermissionGateway: HealthPermissionGateway,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                healthConnectRepository = healthConnectRepository,
                appStateRepository = appStateRepository,
                authRepository = authRepository,
                syncScheduler = syncScheduler,
                healthPermissionGateway = healthPermissionGateway,
            ) as T
        }
    }
}

data class MainScreenState(
    val healthStatus: HealthConnectStatus? = null,
    val sessionState: GripGainsSessionState = GripGainsSessionState(),
    val tokenDraft: String = "",
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
    tokenDraft: String,
): MainScreenState {
    return MainScreenState(
        healthStatus = healthStatus,
        sessionState = sessionState,
        tokenDraft = tokenDraft,
        lastWeight = lastWeight,
        lastSubmittedWeight = lastSubmittedWeight,
        lastSyncAttemptAt = lastSyncAttemptAt,
        lastSyncSuccessAt = lastSyncSuccessAt,
        lastSyncFailureMessage = lastSyncFailureMessage,
        lastSyncSkippedReasonText = lastSyncSkippedReason?.displayText(),
        statusMessage = statusMessage,
    )
}

private fun WeightReading?.toReadMessage(): String {
    return if (this == null) {
        "No weight records found."
    } else {
        "Read ${"%.1f".format(kilograms)} kg from Health Connect."
    }
}

private fun SyncSkipReason.displayText(): String {
    return when (this) {
        SyncSkipReason.HEALTH_CONNECT_UNAVAILABLE -> "Health Connect unavailable"
        SyncSkipReason.WEIGHT_PERMISSION_MISSING -> "Weight permission missing"
        SyncSkipReason.BACKGROUND_READ_UNAVAILABLE -> "Background read unavailable"
        SyncSkipReason.NO_WEIGHT_DATA -> "No weight data"
        SyncSkipReason.MISSING_SESSION -> "No Grip Gains token saved"
        SyncSkipReason.INVALID_SESSION -> "Grip Gains session is invalid"
    }
}
