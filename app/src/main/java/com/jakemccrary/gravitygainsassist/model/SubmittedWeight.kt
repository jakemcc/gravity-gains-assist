package com.jakemccrary.gravitygainsassist.model

import java.time.LocalDate

data class SubmittedWeight(
    val date: LocalDate,
    val weightLbs: Double,
)
