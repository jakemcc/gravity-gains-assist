package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.model.WeightReading
import java.time.Instant
import java.time.ZoneId

object SyncPreferenceTimeSelector {
    fun preferredMinutes(
        latestWeight: WeightReading?,
        syncedAt: Instant,
        zoneId: ZoneId,
    ): Int {
        val localTime = (latestWeight?.recordedAt ?: syncedAt).atZone(zoneId).toLocalTime()
        return (localTime.hour * 60) + localTime.minute
    }
}
