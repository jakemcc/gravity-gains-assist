package com.jakemccrary.gravitygainsassist.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.jakemccrary.gravitygainsassist.model.SyncFailureKind
import com.jakemccrary.gravitygainsassist.model.SyncOutcome
import com.jakemccrary.gravitygainsassist.model.SyncTrigger
import java.time.Duration

class DailySyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val delegate: DailySyncWorkerDelegate,
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

class DailySyncWorkerDelegate(
    private val syncCoordinator: SyncCoordinator,
    private val syncScheduler: SyncScheduler,
    private val networkRetryDelay: Duration = Duration.ofMinutes(2),
) {
    suspend fun run(isNetworkRetry: Boolean = false): ListenableWorker.Result {
        return when (
            val outcome = syncCoordinator.runSync(
                if (isNetworkRetry) SyncTrigger.NETWORK_RETRY else SyncTrigger.BACKGROUND_WORK,
            )
        ) {
            is SyncOutcome.Succeeded,
            is SyncOutcome.Skipped -> ListenableWorker.Result.success()

            is SyncOutcome.Failed -> {
                if (outcome.kind == SyncFailureKind.NETWORK && !isNetworkRetry) {
                    syncScheduler.scheduleNetworkRetry(networkRetryDelay)
                }
                ListenableWorker.Result.success()
            }
        }
    }
}

class AppWorkerFactory(
    private val syncCoordinator: SyncCoordinator,
    private val syncScheduler: SyncScheduler,
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
                    delegate = DailySyncWorkerDelegate(
                        syncCoordinator = syncCoordinator,
                        syncScheduler = syncScheduler,
                    ),
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
