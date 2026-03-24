package com.jakemccrary.gravitygainsassist.website

import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.WeightReading
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId

class GripGainsPayloadMapper(
    private val zoneId: ZoneId,
) {
    fun map(reading: WeightReading): SubmittedWeight {
        return SubmittedWeight(
            date = reading.recordedAt.atZone(zoneId).toLocalDate(),
            weightLbs = reading.kilograms.toPounds(),
        )
    }
}

private fun Double.toPounds(): Double {
    return BigDecimal(this * KILOGRAMS_TO_POUNDS)
        .setScale(1, RoundingMode.HALF_UP)
        .toDouble()
}

private const val KILOGRAMS_TO_POUNDS = 2.2046226218
