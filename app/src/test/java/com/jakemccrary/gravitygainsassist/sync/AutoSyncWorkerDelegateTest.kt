package com.jakemccrary.gravitygainsassist.sync

import androidx.work.ListenableWorker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncWorkerDelegateTest {
    @Test
    fun `successful auto sync run maps to worker success`() = runTest {
        val delegate = AutoSyncWorkerDelegate(
            autoSyncCoordinator = object : AutoSyncCoordinator {
                override suspend fun setEnabled(enabled: Boolean) = Unit

                override suspend fun scheduleIfEnabled() = Unit

                override suspend fun runAutoSync() = Unit
            },
        )

        val result = delegate.run()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `unexpected auto sync failure maps to worker retry`() = runTest {
        val delegate = AutoSyncWorkerDelegate(
            autoSyncCoordinator = object : AutoSyncCoordinator {
                override suspend fun setEnabled(enabled: Boolean) = Unit

                override suspend fun scheduleIfEnabled() = Unit

                override suspend fun runAutoSync() {
                    throw IllegalStateException("boom")
                }
            },
        )

        val result = delegate.run()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
