package com.jakemccrary.gravitygainsassist.health

import com.jakemccrary.gravitygainsassist.model.WeightReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LatestWeightSelectorTest {
    @Test
    fun `returns null when there are no weight readings`() {
        assertNull(LatestWeightSelector.select(emptyList()))
    }

    @Test
    fun `selects the reading with the latest timestamp`() {
        val earliest = WeightReading(81.0, Instant.parse("2026-03-01T10:15:30Z"))
        val latest = WeightReading(79.5, Instant.parse("2026-03-03T10:15:30Z"))
        val middle = WeightReading(82.5, Instant.parse("2026-03-02T10:15:30Z"))

        val selected = LatestWeightSelector.select(listOf(middle, latest, earliest))

        assertEquals(latest, selected)
    }
}
