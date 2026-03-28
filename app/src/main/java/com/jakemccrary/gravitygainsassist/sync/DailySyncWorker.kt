package com.jakemccrary.gravitygainsassist.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.jakemccrary.gravitygainsassist.model.SyncOutcome
import com.jakemccrary.gravitygainsassist.model.SyncTrigger

class DailySyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val delegate: DailySyncWorkerDelegate,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return delegate.run()
    }
}

class DailySyncWorkerDelegate(
    private val syncCoordinator: SyncCoordinator,
) {
    suspend fun run(): ListenableWorker.Result {
        return when (syncCoordinator.runSync(SyncTrigger.BACKGROUND_WORK)) {
            is SyncOutcome.Succeeded,
            is SyncOutcome.Skipped -> ListenableWorker.Result.success()

            is SyncOutcome.Failed -> ListenableWorker.Result.retry()
        }
    }
}

class AppWorkerFactory(
    private val syncCoordinator: SyncCoordinator,
    private val autoSyncCoordinator: AutoSyncCoordinator,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            DailySyncWorker::class.qualifiedName -> {
                DailySyncWorker(
                    appContext = appContext,
                    workerParameters = workerParameters,
                    delegate = DailySyncWorkerDelegate(syncCoordinator),
                )
            }

            AutoSyncWorker::class.qualifiedName -> {
                AutoSyncWorker(
                    appContext = appContext,
                    workerParameters = workerParameters,
                    delegate = AutoSyncWorkerDelegate(autoSyncCoordinator),
                )
            }

            else -> null
        }
    }
}
