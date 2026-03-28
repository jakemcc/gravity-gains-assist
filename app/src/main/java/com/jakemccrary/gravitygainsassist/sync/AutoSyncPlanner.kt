package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.model.AppState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AutoSyncPlanner(
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val retryInterval: Duration = Duration.ofMinutes(90),
    private val nextDayLeadTime: Duration = Duration.ofMinutes(30),
    private val defaultPreferredTime: LocalTime = LocalTime.of(7, 30),
) {
    fun nextCheckWhenEnabled(appState: AppState): Instant {
        return if (isTodayAlreadySynced(appState)) {
            nextCheckAfterDayComplete(appState)
        } else {
            clock.instant()
        }
    }

    fun nextCheckAfterDayComplete(appState: AppState): Instant {
        val tomorrow = today().plusDays(1)
        return nextDayFirstCheckAt(appState, tomorrow)
    }

    fun nextCheckAfterMiss(appState: AppState): Instant {
        val retryAt = clock.instant().plus(retryInterval)
        val tomorrow = today().plusDays(1)
        return if (retryAt.atZone(zoneId).toLocalDate() != today()) {
            nextDayFirstCheckAt(appState, tomorrow)
        } else {
            retryAt
        }
    }

    fun isTodayAlreadySynced(appState: AppState): Boolean {
        return appState.lastSubmittedWeight?.date == today()
    }

    private fun today(): LocalDate = clock.instant().atZone(zoneId).toLocalDate()

    private fun nextDayFirstCheckAt(appState: AppState, date: LocalDate): Instant {
        val preferredMinutes = appState.preferredNextSyncMinutesOfDay
            ?: minutesOfDay(defaultPreferredTime)
        val preferredTime = LocalTime.of(preferredMinutes / 60, preferredMinutes % 60)
        return date.atTime(preferredTime)
            .minus(nextDayLeadTime)
            .atZone(zoneId)
            .toInstant()
    }

    private fun minutesOfDay(localTime: LocalTime): Int {
        return (localTime.hour * 60) + localTime.minute
    }
}
