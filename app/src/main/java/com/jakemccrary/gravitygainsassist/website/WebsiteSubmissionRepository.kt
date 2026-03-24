package com.jakemccrary.gravitygainsassist.website

import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.WeightReading
import java.io.IOException

interface WebsiteSubmissionRepository {
    suspend fun submitWeight(reading: WeightReading): SubmissionResult
}

class GripGainsWebsiteSubmissionRepository(
    private val authRepository: AuthRepository,
    private val requestFactory: GripGainsRequestFactory,
    private val gripGainsApi: GripGainsApi,
) : WebsiteSubmissionRepository {
    override suspend fun submitWeight(reading: WeightReading): SubmissionResult {
        return when (authRepository.getSessionState().status) {
            GripGainsSessionState.Status.NO_TOKEN -> SubmissionResult.MissingSession
            GripGainsSessionState.Status.INVALID_SESSION -> SubmissionResult.InvalidSession
            GripGainsSessionState.Status.TOKEN_PRESENT -> submitWithActiveSession(reading)
        }
    }

    private suspend fun submitWithActiveSession(reading: WeightReading): SubmissionResult {
        val session = authRepository.getActiveSession() ?: return SubmissionResult.MissingSession
        val preparedSubmission = requestFactory.create(reading, session)

        return try {
            when (val statusCode = gripGainsApi.execute(preparedSubmission.request).statusCode) {
                in 200..299 -> {
                    authRepository.markSessionValid()
                    SubmissionResult.Success(preparedSubmission.submittedWeight)
                }

                401,
                403 -> {
                    authRepository.markSessionInvalid()
                    SubmissionResult.AuthExpired
                }

                else -> SubmissionResult.ServerFailure(statusCode)
            }
        } catch (exception: IOException) {
            SubmissionResult.NetworkFailure(exception)
        } catch (throwable: Throwable) {
            SubmissionResult.UnknownFailure(throwable)
        }
    }
}

sealed interface SubmissionResult {
    data class Success(val submittedWeight: SubmittedWeight) : SubmissionResult

    data object MissingSession : SubmissionResult

    data object InvalidSession : SubmissionResult

    data object AuthExpired : SubmissionResult

    data class NetworkFailure(val cause: IOException) : SubmissionResult

    data class ServerFailure(val statusCode: Int) : SubmissionResult

    data class UnknownFailure(val cause: Throwable) : SubmissionResult
}
