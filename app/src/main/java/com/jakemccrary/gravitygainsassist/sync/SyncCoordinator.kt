package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.data.AppStateRepository
import com.jakemccrary.gravitygainsassist.health.HealthConnectRepository
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.SyncFailureKind
import com.jakemccrary.gravitygainsassist.model.SyncOutcome
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.SyncTrigger
import com.jakemccrary.gravitygainsassist.website.SubmissionResult
import com.jakemccrary.gravitygainsassist.website.WebsiteSubmissionRepository
import java.time.Clock

interface SyncCoordinator {
    suspend fun runSync(trigger: SyncTrigger): SyncOutcome
}

class DefaultSyncCoordinator(
    private val healthConnectRepository: HealthConnectRepository,
    private val appStateRepository: AppStateRepository,
    private val websiteSubmissionRepository: WebsiteSubmissionRepository,
    private val clock: Clock,
    private val zoneId: java.time.ZoneId,
    private val syncFailureNotifier: SyncFailureNotifier = NoOpSyncFailureNotifier,
) : SyncCoordinator {
    override suspend fun runSync(trigger: SyncTrigger): SyncOutcome {
        val attemptAt = clock.instant()
        appStateRepository.recordSyncAttempt(attemptAt)

        return try {
            val healthStatus = healthConnectRepository.getStatus()
            appStateRepository.recordHealthConnectStatus(healthStatus)
            when {
                healthStatus.availability != HealthConnectAvailability.AVAILABLE -> {
                    skip(SyncSkipReason.HEALTH_CONNECT_UNAVAILABLE)
                }

                !healthStatus.isWeightPermissionGranted -> {
                    skip(SyncSkipReason.WEIGHT_PERMISSION_MISSING)
                }

                trigger == SyncTrigger.BACKGROUND_WORK &&
                    (!healthStatus.isBackgroundReadFeatureAvailable ||
                        !healthStatus.isBackgroundReadPermissionGranted) -> {
                    skip(SyncSkipReason.BACKGROUND_READ_UNAVAILABLE)
                }

                else -> {
                    val latestWeight = healthConnectRepository.readLatestWeight()
                    if (latestWeight == null) {
                        skip(SyncSkipReason.NO_WEIGHT_DATA)
                    } else {
                        handleSubmissionResult(
                            latestWeight = latestWeight,
                            submissionResult = websiteSubmissionRepository.submitWeight(latestWeight),
                            trigger = trigger,
                        )
                    }
                }
            }
        } catch (throwable: Throwable) {
            recordFailure("Unexpected sync failure.")
            SyncOutcome.Failed(
                cause = throwable,
                kind = SyncFailureKind.UNKNOWN,
            )
        }
    }

    private suspend fun skip(reason: SyncSkipReason): SyncOutcome {
        appStateRepository.recordSyncSkipped(reason)
        return SyncOutcome.Skipped(reason)
    }

    private suspend fun handleSubmissionResult(
        latestWeight: com.jakemccrary.gravitygainsassist.model.WeightReading,
        submissionResult: SubmissionResult,
        trigger: SyncTrigger,
    ): SyncOutcome {
        return when (submissionResult) {
            is SubmissionResult.Success -> {
                val successAt = clock.instant()
                appStateRepository.recordSyncSuccess(
                    at = successAt,
                    latestWeight = latestWeight,
                    submittedWeight = submissionResult.submittedWeight,
                    preferredNextSyncMinutesOfDay = SyncPreferenceTimeSelector.preferredMinutes(
                        latestWeight = latestWeight,
                        syncedAt = successAt,
                        zoneId = zoneId,
                    ),
                )
                syncFailureNotifier.notifySuccess(SYNC_SUCCESS_MESSAGE)
                SyncOutcome.Succeeded(latestWeight)
            }

            SubmissionResult.MissingSession -> skip(SyncSkipReason.MISSING_SESSION)

            SubmissionResult.InvalidSession,
            SubmissionResult.AuthExpired -> skip(SyncSkipReason.INVALID_SESSION)

            is SubmissionResult.NetworkFailure -> {
                val failureMessage = if (trigger == SyncTrigger.NETWORK_RETRY) {
                    NETWORK_RETRY_FAILED_MESSAGE
                } else {
                    NETWORK_RETRY_SCHEDULED_MESSAGE
                }
                recordFailure(failureMessage)
                SyncOutcome.Failed(
                    cause = submissionResult.cause,
                    kind = SyncFailureKind.NETWORK,
                )
            }

            is SubmissionResult.ServerFailure -> {
                val failureMessage = submissionResult.responseMessage?.let { responseMessage ->
                    "Grip Gains rejected the submission: $responseMessage"
                } ?: "Grip Gains rejected the submission with HTTP ${submissionResult.statusCode}."
                recordFailure(failureMessage)
                SyncOutcome.Failed(
                    cause = IllegalStateException(failureMessage),
                    kind = SyncFailureKind.SERVER,
                )
            }

            is SubmissionResult.UnknownFailure -> {
                recordFailure("Unexpected failure while submitting to Grip Gains.")
                SyncOutcome.Failed(
                    cause = submissionResult.cause,
                    kind = SyncFailureKind.UNKNOWN,
                )
            }
        }
    }

    private suspend fun recordFailure(message: String) {
        appStateRepository.recordSyncFailure(message)
        syncFailureNotifier.notifyFailure(message)
    }

    private companion object {
        const val NETWORK_RETRY_SCHEDULED_MESSAGE =
            "Network error while submitting to Grip Gains. Retrying once in 2 minutes."
        const val NETWORK_RETRY_FAILED_MESSAGE =
            "Network error while submitting to Grip Gains. Use Run sync now to try again."
        const val SYNC_SUCCESS_MESSAGE = "Synced weight to Grip Gains."
    }
}
