package com.jakemccrary.gravitygainsassist.website

import com.jakemccrary.gravitygainsassist.model.WeightReading
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class GripGainsRequestFactoryTest {
    @Test
    fun `creates exact Grip Gains request shape`() {
        val factory = GripGainsRequestFactory(
            payloadMapper = GripGainsPayloadMapper(ZoneId.of("UTC")),
        )
        val reading = WeightReading(
            kilograms = 67.314,
            recordedAt = Instant.parse("2026-03-24T04:15:00Z"),
        )

        val prepared = factory.create(
            reading,
            GripGainsSession(
                token = "jwt-token",
                cookieHeader = "csrftoken=abc; grip_gains_token=jwt-token",
            ),
        )

        assertEquals("POST", prepared.request.method)
        assertEquals("https://gripgains.ca/api/bodyweight/", prepared.request.url)
        assertEquals("""{"date":"2026-03-24","weight_lbs":148.4}""", prepared.request.body)
        assertEquals("application/json", prepared.request.headers["Content-Type"])
        assertEquals("application/json", prepared.request.headers["Accept"])
        assertEquals("https://gripgains.ca/gravity-gains", prepared.request.headers["Referer"])
        assertEquals("https://gripgains.ca", prepared.request.headers["Origin"])
        assertEquals("Bearer jwt-token", prepared.request.headers["Authorization"])
        assertEquals("csrftoken=abc; grip_gains_token=jwt-token", prepared.request.headers["Cookie"])
    }
}
