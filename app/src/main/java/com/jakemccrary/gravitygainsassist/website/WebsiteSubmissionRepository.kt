package com.jakemccrary.gravitygainsassist.website

import android.util.Log
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
        logSubmissionAttempt(preparedSubmission.request)

        return try {
            val response = gripGainsApi.execute(preparedSubmission.request)
            logSubmissionResponse(response)

            when (response.statusCode) {
                in 200..299 -> {
                    infoLog("Grip Gains submission succeeded with HTTP ${response.statusCode}.")
                    authRepository.markSessionValid()
                    SubmissionResult.Success(preparedSubmission.submittedWeight)
                }

                401,
                403 -> {
                    warnLog(
                        "Grip Gains auth rejected submission with HTTP ${response.statusCode}. " +
                            "body=${response.responseBody.orEmpty().truncateForLog()}",
                    )
                    authRepository.markSessionInvalid()
                    SubmissionResult.AuthExpired
                }

                else -> {
                    SubmissionResult.ServerFailure(
                        statusCode = response.statusCode,
                        responseMessage = response.responseBody.toServerFailureMessage(),
                    )
                }
            }
        } catch (exception: IOException) {
            warnLog("Grip Gains submission failed with network error.", exception)
            SubmissionResult.NetworkFailure(exception)
        } catch (throwable: Throwable) {
            errorLog("Grip Gains submission failed unexpectedly.", throwable)
            SubmissionResult.UnknownFailure(throwable)
        }
    }

    private fun logSubmissionAttempt(request: GripGainsRequest) {
        debugLog(
            "Submitting Grip Gains weight request. " +
                "url=${request.url}, body=${request.body}, " +
                "hasAuthorization=${request.headers.containsKey("Authorization")}, " +
                "cookieNames=${request.headers["Cookie"].orEmpty().cookieNamesForLog()}",
        )
    }

    private fun logSubmissionResponse(response: GripGainsApiResponse) {
        if (response.statusCode in 200..299) {
            return
        }

        warnLog(
            "Grip Gains response HTTP ${response.statusCode}. " +
                "body=${response.responseBody.orEmpty().truncateForLog()}",
        )
    }
}

sealed interface SubmissionResult {
    data class Success(val submittedWeight: SubmittedWeight) : SubmissionResult

    data object MissingSession : SubmissionResult

    data object InvalidSession : SubmissionResult

    data object AuthExpired : SubmissionResult

    data class NetworkFailure(val cause: IOException) : SubmissionResult

    data class ServerFailure(
        val statusCode: Int,
        val responseMessage: String? = null,
    ) : SubmissionResult

    data class UnknownFailure(val cause: Throwable) : SubmissionResult
}

private fun String.cookieNamesForLog(): String {
    if (isBlank()) {
        return "none"
    }

    return split(";")
        .mapNotNull { cookie ->
            val separatorIndex = cookie.indexOf('=')
            if (separatorIndex <= 0) {
                null
            } else {
                cookie.substring(0, separatorIndex).trim()
            }
        }
        .joinToString(",")
        .ifBlank { "none" }
}

private fun String.truncateForLog(maxLength: Int = 500): String {
    return if (length <= maxLength) this else take(maxLength) + "..."
}

private fun String?.toServerFailureMessage(): String? {
    val normalizedBody = this?.trim().orEmpty()
    if (normalizedBody.isBlank()) {
        return null
    }

    val detailMatch = Regex(""""detail"\s*:\s*"([^"]+)"""")
        .find(normalizedBody)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    return detailMatch?.ifBlank { null } ?: normalizedBody
}

private const val TAG = "GripGainsSubmission"

private fun debugLog(message: String) {
    runCatching { Log.d(TAG, message) }
        .getOrElse { println("D/$TAG: $message") }
}

private fun infoLog(message: String) {
    runCatching { Log.i(TAG, message) }
        .getOrElse { println("I/$TAG: $message") }
}

private fun warnLog(message: String, throwable: Throwable? = null) {
    runCatching {
        if (throwable == null) {
            Log.w(TAG, message)
        } else {
            Log.w(TAG, message, throwable)
        }
    }.getOrElse {
        println("W/$TAG: $message")
        throwable?.printStackTrace()
    }
}

private fun errorLog(message: String, throwable: Throwable) {
    runCatching { Log.e(TAG, message, throwable) }
        .getOrElse {
            println("E/$TAG: $message")
            throwable.printStackTrace()
        }
}
