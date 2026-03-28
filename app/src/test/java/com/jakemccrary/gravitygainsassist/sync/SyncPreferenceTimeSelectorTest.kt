package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.model.WeightReading
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SyncPreferenceTimeSelectorTest {
    private val zoneId: ZoneId = ZoneId.of("America/Chicago")

    @Test
    fun `uses the recorded time of the synced weight when available`() {
        val weight = WeightReading(
            kilograms = 82.4,
            recordedAt = Instant.parse("2026-03-27T12:00:00Z"),
        )

        val preferredMinutes = SyncPreferenceTimeSelector.preferredMinutes(
            latestWeight = weight,
            syncedAt = Instant.parse("2026-03-27T15:45:00Z"),
            zoneId = zoneId,
        )

        assertEquals(7 * 60, preferredMinutes)
    }

    @Test
    fun `falls back to the sync time when no weight is available`() {
        val preferredMinutes = SyncPreferenceTimeSelector.preferredMinutes(
            latestWeight = null,
            syncedAt = Instant.parse("2026-03-27T15:45:00Z"),
            zoneId = zoneId,
        )

        assertEquals((10 * 60) + 45, preferredMinutes)
    }
}
