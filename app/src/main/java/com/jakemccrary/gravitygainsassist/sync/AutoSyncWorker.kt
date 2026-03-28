package com.jakemccrary.gravitygainsassist.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

class AutoSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val delegate: AutoSyncWorkerDelegate,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return delegate.run()
    }
}

class AutoSyncWorkerDelegate(
    private val autoSyncCoordinator: AutoSyncCoordinator,
) {
    suspend fun run(): ListenableWorker.Result {
        return runCatching {
            autoSyncCoordinator.runAutoSync()
            ListenableWorker.Result.success()
        }.getOrElse {
            ListenableWorker.Result.retry()
        }
    }
}
