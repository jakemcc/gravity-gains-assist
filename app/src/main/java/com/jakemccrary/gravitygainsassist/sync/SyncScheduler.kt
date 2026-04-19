package com.jakemccrary.gravitygainsassist.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

interface SyncScheduler {
    fun enqueueImmediateSync()

    fun scheduleNetworkRetry(delay: Duration)

    fun replaceAutoSync(at: Instant)

    fun scheduleNextAutoSync(at: Instant)

    fun scheduleNetworkRetryAutoSync(at: Instant)

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
            dailySyncRequest(
                initialDelay = Duration.ZERO,
                inputData = Data.EMPTY,
            ),
        )
    }

    override fun scheduleNetworkRetry(delay: Duration) {
        workManager.enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            dailySyncRequest(
                initialDelay = delay,
                inputData = workDataOf(DailySyncWorker.IS_NETWORK_RETRY_KEY to true),
            ),
        )
    }

    override fun replaceAutoSync(at: Instant) {
        cancelAutoSync()
        enqueueAutoSync(
            at = at,
            existingWorkPolicy = policyForFollowUp(at),
            inputData = Data.EMPTY,
        )
    }

    override fun scheduleNextAutoSync(at: Instant) {
        enqueueAutoSync(
            at = at,
            existingWorkPolicy = policyForFollowUp(at),
            inputData = Data.EMPTY,
        )
    }

    override fun scheduleNetworkRetryAutoSync(at: Instant) {
        enqueueAutoSync(
            at = at,
            existingWorkPolicy = ExistingWorkPolicy.REPLACE,
            inputData = workDataOf(AutoSyncWorker.IS_NETWORK_RETRY_KEY to true),
        )
    }

    override fun cancelAutoSync() {
        workManager.cancelAllWorkByTag(AUTO_SYNC_TAG)
    }

    private fun dailySyncRequest(
        initialDelay: Duration,
        inputData: Data,
    ) = OneTimeWorkRequestBuilder<DailySyncWorker>()
        .setInitialDelay(initialDelay)
        .setInputData(inputData)
        .build()

    private fun enqueueAutoSync(
        at: Instant,
        existingWorkPolicy: ExistingWorkPolicy,
        inputData: Data,
    ) {
        workManager.enqueueUniqueWork(
            autoSyncWorkName(at),
            existingWorkPolicy,
            OneTimeWorkRequestBuilder<AutoSyncWorker>()
                .setInitialDelay(initialDelay(at))
                .setConstraints(networkConstraints())
                .setInputData(inputData)
                .addTag(AUTO_SYNC_TAG)
                .build(),
        )
    }

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
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
