package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.model.AppState
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AutoSyncPlannerTest {
    private val zoneId: ZoneId = ZoneId.of("America/Chicago")

    @Test
    fun `enabling auto sync schedules immediately when today is not yet synced`() {
        val planner = AutoSyncPlanner(
            clock = Clock.fixed(Instant.parse("2026-03-27T14:30:00Z"), ZoneOffset.UTC),
            zoneId = zoneId,
        )

        val scheduledAt = planner.nextCheckWhenEnabled(AppState(autoSyncEnabled = true))

        assertEquals(Instant.parse("2026-03-27T14:30:00Z"), scheduledAt)
    }

    @Test
    fun `next day first check is thirty minutes before the prior successful sync time`() {
        val planner = AutoSyncPlanner(
            clock = Clock.fixed(Instant.parse("2026-03-27T13:15:00Z"), ZoneOffset.UTC),
            zoneId = zoneId,
        )
        val appState = AppState(
            lastSyncSuccessAt = Instant.parse("2026-03-27T12:12:00Z"),
            lastSubmittedWeight = SubmittedWeight(
                date = LocalDate.parse("2026-03-27"),
                weightLbs = 181.2,
            ),
            preferredNextSyncMinutesOfDay = (7 * 60) + 12,
        )

        val scheduledAt = planner.nextCheckAfterDayComplete(appState)

        assertEquals(Instant.parse("2026-03-28T11:42:00Z"), scheduledAt)
    }

    @Test
    fun `retry after a miss is ninety minutes later when still within the same day`() {
        val planner = AutoSyncPlanner(
            clock = Clock.fixed(Instant.parse("2026-03-27T15:00:00Z"), ZoneOffset.UTC),
            zoneId = zoneId,
        )

        val scheduledAt = planner.nextCheckAfterMiss(AppState())

        assertEquals(Instant.parse("2026-03-27T16:30:00Z"), scheduledAt)
    }

    @Test
    fun `retry near the end of the day rolls over to the next day first check`() {
        val planner = AutoSyncPlanner(
            clock = Clock.fixed(Instant.parse("2026-03-28T03:30:00Z"), ZoneOffset.UTC),
            zoneId = zoneId,
            retryInterval = Duration.ofMinutes(90),
        )
        val appState = AppState(
            preferredNextSyncMinutesOfDay = (7 * 60) + 12,
        )

        val scheduledAt = planner.nextCheckAfterMiss(appState)

        assertEquals(Instant.parse("2026-03-28T11:42:00Z"), scheduledAt)
    }
}
