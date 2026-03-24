package com.jakemccrary.gravitygainsassist.sync

import androidx.work.ListenableWorker
import com.jakemccrary.gravitygainsassist.model.SyncOutcome
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.SyncTrigger
import com.jakemccrary.gravitygainsassist.model.WeightReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DailySyncWorkerDelegateTest {
    @Test
    fun `successful sync maps to worker success`() = runTest {
        val delegate = DailySyncWorkerDelegate(
            syncCoordinator = FakeSyncCoordinator(
                outcome = SyncOutcome.Succeeded(
                    WeightReading(80.0, Instant.parse("2026-03-24T07:00:00Z")),
                ),
            ),
        )

        val result = delegate.run()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `skipped sync maps to worker success`() = runTest {
        val delegate = DailySyncWorkerDelegate(
            syncCoordinator = FakeSyncCoordinator(
                outcome = SyncOutcome.Skipped(SyncSkipReason.NO_WEIGHT_DATA),
            ),
        )

        val result = delegate.run()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `failed sync maps to worker retry`() = runTest {
        val delegate = DailySyncWorkerDelegate(
            syncCoordinator = FakeSyncCoordinator(
                outcome = SyncOutcome.Failed(IllegalStateException("nope")),
            ),
        )

        val result = delegate.run()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    private class FakeSyncCoordinator(
        private val outcome: SyncOutcome,
    ) : SyncCoordinator {
        override suspend fun runSync(trigger: SyncTrigger): SyncOutcome = outcome
    }
}
