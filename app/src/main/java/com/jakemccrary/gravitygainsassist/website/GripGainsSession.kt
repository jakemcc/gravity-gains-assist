package com.jakemccrary.gravitygainsassist.website

data class GripGainsSession(
    val token: String,
    val cookieHeader: String,
)

fun defaultGripGainsCookieHeader(token: String): String = "$GRIP_GAINS_TOKEN_COOKIE_NAME=$token"

const val GRIP_GAINS_TOKEN_COOKIE_NAME = "grip_gains_token"

data class GripGainsSessionState(
    val status: Status = Status.NO_TOKEN,
) {
    enum class Status {
        NO_TOKEN,
        TOKEN_PRESENT,
        INVALID_SESSION,
    }
}
