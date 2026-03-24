package com.jakemccrary.gravitygainsassist.website

data class GripGainsSession(
    val token: String,
)

data class GripGainsSessionState(
    val status: Status = Status.NO_TOKEN,
) {
    enum class Status {
        NO_TOKEN,
        TOKEN_PRESENT,
        INVALID_SESSION,
    }
}
