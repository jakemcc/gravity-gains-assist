package com.jakemccrary.gravitygainsassist.model

enum class SyncSkipReason {
    HEALTH_CONNECT_UNAVAILABLE,
    WEIGHT_PERMISSION_MISSING,
    BACKGROUND_READ_UNAVAILABLE,
    NO_WEIGHT_DATA,
    MISSING_SESSION,
    INVALID_SESSION,
}
