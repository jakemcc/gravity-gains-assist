package com.jakemccrary.gravitygainsassist.model

import java.time.Instant

data class AppState(
    val lastWeight: WeightReading? = null,
    val lastSyncAttemptAt: Instant? = null,
    val lastSyncSuccessAt: Instant? = null,
    val lastSyncSkippedReason: SyncSkipReason? = null,
    val lastSyncFailureMessage: String? = null,
    val lastSubmittedWeight: SubmittedWeight? = null,
)
