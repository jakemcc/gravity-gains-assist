package com.jakemccrary.gravitygainsassist.model

sealed interface SyncOutcome {
    data class Succeeded(val reading: WeightReading) : SyncOutcome

    data class Skipped(val reason: SyncSkipReason) : SyncOutcome

    data class Failed(
        val cause: Throwable,
        val kind: SyncFailureKind = SyncFailureKind.UNKNOWN,
    ) : SyncOutcome
}

enum class SyncFailureKind {
    NETWORK,
    SERVER,
    UNKNOWN,
}
