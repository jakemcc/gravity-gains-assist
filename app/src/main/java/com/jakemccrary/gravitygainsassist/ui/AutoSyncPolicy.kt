package com.jakemccrary.gravitygainsassist.ui

import com.jakemccrary.gravitygainsassist.model.AppState
import java.time.Clock
import java.time.ZoneId

class AutoSyncPolicy(
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    fun shouldEnqueueSync(appState: AppState): Boolean {
        if (!appState.autoSyncEnabled) {
            return false
        }

        val today = clock.instant().atZone(zoneId).toLocalDate()
        val lastSuccessDate = appState.lastSyncSuccessAt?.atZone(zoneId)?.toLocalDate()
        return lastSuccessDate != today
    }
}
