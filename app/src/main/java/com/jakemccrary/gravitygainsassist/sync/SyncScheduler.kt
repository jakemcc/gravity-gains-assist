package com.jakemccrary.gravitygainsassist.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

interface SyncScheduler {
    fun scheduleDailySync()

    fun enqueueImmediateSync()
}

class WorkManagerSyncScheduler(
    context: Context,
) : SyncScheduler {
    private val appContext = context.applicationContext

    override fun scheduleDailySync() {
        workManager.enqueueUniquePeriodicWork(
            DAILY_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    override fun enqueueImmediateSync() {
        workManager.enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailySyncWorker>().build(),
        )
    }

    private val workManager: WorkManager
        get() = WorkManager.getInstance(appContext)

    private companion object {
        const val DAILY_SYNC_WORK_NAME = "daily_sync"
        const val IMMEDIATE_SYNC_WORK_NAME = "immediate_sync"
    }
}
