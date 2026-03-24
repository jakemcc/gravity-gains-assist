package com.jakemccrary.gravitygainsassist.model

sealed interface SyncOutcome {
    data class Succeeded(val reading: WeightReading) : SyncOutcome

    data class Skipped(val reason: SyncSkipReason) : SyncOutcome

    data class Failed(
        val cause: Throwable,
        val isRetryable: Boolean = true,
    ) : SyncOutcome
}
