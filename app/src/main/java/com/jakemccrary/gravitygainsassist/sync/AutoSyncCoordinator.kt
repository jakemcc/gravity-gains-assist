package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.data.AppStateRepository
import com.jakemccrary.gravitygainsassist.health.HealthConnectRepository
import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.WeightReading
import com.jakemccrary.gravitygainsassist.website.AuthRepository
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import com.jakemccrary.gravitygainsassist.website.SubmissionResult
import com.jakemccrary.gravitygainsassist.website.WebsiteSubmissionRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface AutoSyncCoordinator {
    suspend fun setEnabled(enabled: Boolean)

    suspend fun scheduleIfEnabled()

    suspend fun runAutoSync()
}

class DefaultAutoSyncCoordinator(
    private val healthConnectRepository: HealthConnectRepository,
    private val appStateRepository: AppStateRepository,
    private val authRepository: AuthRepository,
    private val websiteSubmissionRepository: WebsiteSubmissionRepository,
    private val syncScheduler: SyncScheduler,
    private val autoSyncPlanner: AutoSyncPlanner,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val syncFailureNotifier: SyncFailureNotifier = NoOpSyncFailureNotifier,
) : AutoSyncCoordinator {
    override suspend fun setEnabled(enabled: Boolean) {
        appStateRepository.setAutoSyncEnabled(enabled)
        if (!enabled) {
            syncScheduler.cancelAutoSync()
            appStateRepository.recordNextAutoSyncCheck(null)
            return
        }

        scheduleCurrentState(appStateRepository.appState.first())
    }

    override suspend fun scheduleIfEnabled() {
        val appState = appStateRepository.appState.first()
        if (!appState.autoSyncEnabled) {
            return
        }

        scheduleCurrentState(appState)
    }

    override suspend fun runAutoSync() {
        val appState = appStateRepository.appState.first()
        if (!appState.autoSyncEnabled) {
            appStateRepository.recordNextAutoSyncCheck(null)
            syncScheduler.cancelAutoSync()
            return
        }

        val attemptAt = clock.instant()
        appStateRepository.recordSyncAttempt(attemptAt)

        if (autoSyncPlanner.isTodayAlreadySynced(appState)) {
            scheduleFollowUp(autoSyncPlanner.nextCheckAfterDayComplete(appState))
            return
        }

        when (authRepository.getSessionState().status) {
            GripGainsSessionState.Status.NO_TOKEN -> {
                appStateRepository.recordSyncSkipped(SyncSkipReason.MISSING_SESSION)
                scheduleFollowUp(autoSyncPlanner.nextCheckAfterDayComplete(appState))
                return
            }

            GripGainsSessionState.Status.INVALID_SESSION -> {
                appStateRepository.recordSyncSkipped(SyncSkipReason.INVALID_SESSION)
                scheduleFollowUp(autoSyncPlanner.nextCheckAfterDayComplete(appState))
                return
            }

            GripGainsSessionState.Status.TOKEN_PRESENT -> Unit
        }

        val healthStatus = healthConnectRepository.getStatus()
        appStateRepository.recordHealthConnectStatus(healthStatus)
        val healthSkipReason = backgroundSkipReason(healthStatus)
        if (healthSkipReason != null) {
            appStateRepository.recordSyncSkipped(healthSkipReason)
            scheduleFollowUp(autoSyncPlanner.nextCheckAfterDayComplete(appStateRepository.appState.first()))
            return
        }

        val today = today()
        val latestWeight = healthConnectRepository.readLatestWeightFor(today)
        if (latestWeight == null) {
            appStateRepository.recordSyncSkipped(SyncSkipReason.NO_WEIGHT_DATA)
            scheduleFollowUp(autoSyncPlanner.nextCheckAfterMiss(appStateRepository.appState.first()))
            return
        }

        when (val submissionResult = websiteSubmissionRepository.submitWeight(latestWeight)) {
            is SubmissionResult.Success -> {
            appStateRepository.recordSyncSuccess(
                    at = attemptAt,
                    latestWeight = latestWeight,
                    submittedWeight = submissionResult.submittedWeight,
                    preferredNextSyncMinutesOfDay = SyncPreferenceTimeSelector.preferredMinutes(
                        latestWeight = latestWeight,
                        syncedAt = attemptAt,
                        zoneId = zoneId,
                    ),
                )
                scheduleFollowUp(autoSyncPlanner.nextCheckAfterDayComplete(appStateRepository.appState.first()))
            }

            SubmissionResult.MissingSession -> {
                appStateRepository.recordSyncSkipped(SyncSkipReason.MISSING_SESSION)
                scheduleFollowUp(autoSyncPlanner.nextCheckAfterDayComplete(appStateRepository.appState.first()))
            }

            SubmissionResult.InvalidSession,
            SubmissionResult.AuthExpired -> {
                appStateRepository.recordSyncSkipped(SyncSkipReason.INVALID_SESSION)
                scheduleFollowUp(autoSyncPlanner.nextCheckAfterDayComplete(appStateRepository.appState.first()))
            }

            is SubmissionResult.NetworkFailure -> {
                recordFailure("Network error while submitting to Grip Gains.")
                scheduleFollowUp(autoSyncPlanner.nextCheckAfterMiss(appStateRepository.appState.first()))
            }

            is SubmissionResult.ServerFailure -> {
                val failureMessage = submissionResult.responseMessage?.let { responseMessage ->
                    "Grip Gains rejected the submission: $responseMessage"
                } ?: "Grip Gains rejected the submission with HTTP ${submissionResult.statusCode}."
                recordFailure(failureMessage)
                scheduleFollowUp(autoSyncPlanner.nextCheckAfterDayComplete(appStateRepository.appState.first()))
            }

            is SubmissionResult.UnknownFailure -> {
                recordFailure("Unexpected failure while submitting to Grip Gains.")
                scheduleFollowUp(autoSyncPlanner.nextCheckAfterMiss(appStateRepository.appState.first()))
            }
        }
    }

    private suspend fun scheduleCurrentState(appState: AppState) {
        val nextCheckAt = autoSyncPlanner.nextCheckWhenEnabled(appState)
        syncScheduler.replaceAutoSync(nextCheckAt)
        appStateRepository.recordNextAutoSyncCheck(nextCheckAt)
    }

    private suspend fun scheduleFollowUp(nextCheckAt: Instant) {
        syncScheduler.scheduleNextAutoSync(nextCheckAt)
        appStateRepository.recordNextAutoSyncCheck(nextCheckAt)
    }

    private suspend fun recordFailure(message: String) {
        appStateRepository.recordSyncFailure(message)
        syncFailureNotifier.notifyFailure(message)
    }

    private fun backgroundSkipReason(status: HealthConnectStatus): SyncSkipReason? {
        return when {
            status.availability != HealthConnectAvailability.AVAILABLE ->
                SyncSkipReason.HEALTH_CONNECT_UNAVAILABLE

            !status.isWeightPermissionGranted ->
                SyncSkipReason.WEIGHT_PERMISSION_MISSING

            !status.isBackgroundReadFeatureAvailable || !status.isBackgroundReadPermissionGranted ->
                SyncSkipReason.BACKGROUND_READ_UNAVAILABLE

            else -> null
        }
    }
    private fun today(): LocalDate = clock.instant().atZone(zoneId).toLocalDate()
}
