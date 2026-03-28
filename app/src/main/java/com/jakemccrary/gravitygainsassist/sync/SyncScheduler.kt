package com.jakemccrary.gravitygainsassist.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

interface SyncScheduler {
    fun enqueueImmediateSync()

    fun replaceAutoSync(at: Instant)

    fun scheduleNextAutoSync(at: Instant)

    fun cancelAutoSync()
}

class WorkManagerSyncScheduler(
    context: Context,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : SyncScheduler {
    private val appContext = context.applicationContext

    override fun enqueueImmediateSync() {
        workManager.enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailySyncWorker>().build(),
        )
    }

    override fun replaceAutoSync(at: Instant) {
        cancelAutoSync()
        enqueueAutoSync(at, policyForFollowUp(at))
    }

    override fun scheduleNextAutoSync(at: Instant) {
        enqueueAutoSync(at, policyForFollowUp(at))
    }

    override fun cancelAutoSync() {
        workManager.cancelAllWorkByTag(AUTO_SYNC_TAG)
    }

    private fun enqueueAutoSync(at: Instant, existingWorkPolicy: ExistingWorkPolicy) {
        workManager.enqueueUniqueWork(
            autoSyncWorkName(at),
            existingWorkPolicy,
            OneTimeWorkRequestBuilder<AutoSyncWorker>()
                .setInitialDelay(initialDelay(at))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .addTag(AUTO_SYNC_TAG)
                .build(),
        )
    }

    private fun initialDelay(at: Instant): Duration {
        val delay = Duration.between(clock.instant(), at)
        return if (delay.isNegative) Duration.ZERO else delay
    }

    private fun policyForFollowUp(at: Instant): ExistingWorkPolicy {
        val nowDate = clock.instant().atZone(zoneId).toLocalDate()
        val scheduledDate = at.atZone(zoneId).toLocalDate()
        return if (scheduledDate == nowDate) {
            ExistingWorkPolicy.APPEND_OR_REPLACE
        } else {
            ExistingWorkPolicy.REPLACE
        }
    }

    private fun autoSyncWorkName(at: Instant): String {
        return "auto_sync_${at.atZone(zoneId).toLocalDate()}"
    }

    private val workManager: WorkManager
        get() = WorkManager.getInstance(appContext)

    private companion object {
        const val IMMEDIATE_SYNC_WORK_NAME = "immediate_sync"
        const val AUTO_SYNC_TAG = "auto_sync"
    }
}
