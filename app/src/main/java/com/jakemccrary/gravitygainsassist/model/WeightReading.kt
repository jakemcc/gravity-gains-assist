package com.jakemccrary.gravitygainsassist.model

import java.time.Instant

data class WeightReading(
    val kilograms: Double,
    val recordedAt: Instant,
)
