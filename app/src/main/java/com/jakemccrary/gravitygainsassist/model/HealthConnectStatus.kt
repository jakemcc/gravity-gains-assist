package com.jakemccrary.gravitygainsassist.model

data class HealthConnectStatus(
    val availability: HealthConnectAvailability,
    val isWeightPermissionGranted: Boolean,
    val isBackgroundReadFeatureAvailable: Boolean,
    val isBackgroundReadPermissionGranted: Boolean,
)
