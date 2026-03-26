package com.jakemccrary.gravitygainsassist.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

interface SyncScheduler {
    fun enqueueImmediateSync()
}

class WorkManagerSyncScheduler(
    context: Context,
) : SyncScheduler {
    private val appContext = context.applicationContext

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
        const val IMMEDIATE_SYNC_WORK_NAME = "immediate_sync"
    }
}
