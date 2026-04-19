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
        return delegate.run(
            isNetworkRetry = inputData.getBoolean(IS_NETWORK_RETRY_KEY, false),
        )
    }

    companion object {
        const val IS_NETWORK_RETRY_KEY = "is_network_retry"
    }
}

class AutoSyncWorkerDelegate(
    private val autoSyncCoordinator: AutoSyncCoordinator,
) {
    suspend fun run(isNetworkRetry: Boolean = false): ListenableWorker.Result {
        return runCatching {
            autoSyncCoordinator.runAutoSync(isNetworkRetry = isNetworkRetry)
            ListenableWorker.Result.success()
        }.getOrElse {
            ListenableWorker.Result.success()
        }
    }
}
