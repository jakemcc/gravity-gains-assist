package com.jakemccrary.gravitygainsassist.model

import java.time.Instant

data class AppState(
    val autoSyncEnabled: Boolean = false,
    val lastWeight: WeightReading? = null,
    val lastSyncAttemptAt: Instant? = null,
    val lastSyncSuccessAt: Instant? = null,
    val preferredNextSyncMinutesOfDay: Int? = null,
    val nextAutoSyncCheckAt: Instant? = null,
    val weightPermissionGranted: Boolean? = null,
    val backgroundReadFeatureAvailable: Boolean? = null,
    val backgroundReadPermissionGranted: Boolean? = null,
    val lastSyncSkippedReason: SyncSkipReason? = null,
    val lastSyncFailureMessage: String? = null,
    val lastSubmittedWeight: SubmittedWeight? = null,
)
