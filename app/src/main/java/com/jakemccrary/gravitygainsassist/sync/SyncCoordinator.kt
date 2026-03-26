package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.data.AppStateRepository
import com.jakemccrary.gravitygainsassist.health.HealthConnectRepository
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
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
    private val syncFailureNotifier: SyncFailureNotifier = NoOpSyncFailureNotifier,
) : SyncCoordinator {
    override suspend fun runSync(trigger: SyncTrigger): SyncOutcome {
        val attemptAt = clock.instant()
        appStateRepository.recordSyncAttempt(attemptAt)

        return try {
            val healthStatus = healthConnectRepository.getStatus()
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
                        )
                    }
                }
            }
        } catch (throwable: Throwable) {
            recordFailure("Unexpected sync failure.")
            SyncOutcome.Failed(throwable)
        }
    }

    private suspend fun skip(reason: SyncSkipReason): SyncOutcome {
        appStateRepository.recordSyncSkipped(reason)
        return SyncOutcome.Skipped(reason)
    }

    private suspend fun handleSubmissionResult(
        latestWeight: com.jakemccrary.gravitygainsassist.model.WeightReading,
        submissionResult: SubmissionResult,
    ): SyncOutcome {
        return when (submissionResult) {
            is SubmissionResult.Success -> {
                appStateRepository.recordSyncSuccess(
                    at = clock.instant(),
                    latestWeight = latestWeight,
                    submittedWeight = submissionResult.submittedWeight,
                )
                SyncOutcome.Succeeded(latestWeight)
            }

            SubmissionResult.MissingSession -> skip(SyncSkipReason.MISSING_SESSION)

            SubmissionResult.InvalidSession,
            SubmissionResult.AuthExpired -> skip(SyncSkipReason.INVALID_SESSION)

            is SubmissionResult.NetworkFailure -> {
                recordFailure("Network error while submitting to Grip Gains.")
                SyncOutcome.Failed(submissionResult.cause)
            }

            is SubmissionResult.ServerFailure -> {
                val failureMessage = submissionResult.responseMessage?.let { responseMessage ->
                    "Grip Gains rejected the submission: $responseMessage"
                } ?: "Grip Gains rejected the submission with HTTP ${submissionResult.statusCode}."
                recordFailure(failureMessage)
                SyncOutcome.Failed(
                    IllegalStateException(
                        failureMessage,
                    ),
                )
            }

            is SubmissionResult.UnknownFailure -> {
                recordFailure("Unexpected failure while submitting to Grip Gains.")
                SyncOutcome.Failed(submissionResult.cause)
            }
        }
    }

    private suspend fun recordFailure(message: String) {
        appStateRepository.recordSyncFailure(message)
        syncFailureNotifier.notifyFailure(message)
    }
}
