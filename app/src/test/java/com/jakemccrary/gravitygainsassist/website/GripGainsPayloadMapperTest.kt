package com.jakemccrary.gravitygainsassist.website

import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.WeightReading
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GripGainsPayloadMapperTest {
    @Test
    fun `maps kilograms to pounds and local date`() {
        val mapper = GripGainsPayloadMapper(ZoneId.of("America/Chicago"))
        val reading = WeightReading(
            kilograms = 67.314,
            recordedAt = Instant.parse("2026-03-24T04:15:00Z"),
        )

        val payload = mapper.map(reading)

        assertEquals(
            SubmittedWeight(
                date = LocalDate.parse("2026-03-23"),
                weightLbs = 148.4,
            ),
            payload,
        )
    }
}
