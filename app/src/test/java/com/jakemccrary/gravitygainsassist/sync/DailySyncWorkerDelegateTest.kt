package com.jakemccrary.gravitygainsassist.sync

import androidx.work.ListenableWorker
import com.jakemccrary.gravitygainsassist.model.SyncFailureKind
import com.jakemccrary.gravitygainsassist.model.SyncOutcome
import com.jakemccrary.gravitygainsassist.model.SyncSkipReason
import com.jakemccrary.gravitygainsassist.model.SyncTrigger
import com.jakemccrary.gravitygainsassist.model.WeightReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
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
            syncScheduler = FakeSyncScheduler(),
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
            syncScheduler = FakeSyncScheduler(),
        )

        val result = delegate.run()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `failed sync maps to worker success so the user controls retries`() = runTest {
        val delegate = DailySyncWorkerDelegate(
            syncCoordinator = FakeSyncCoordinator(
                outcome = SyncOutcome.Failed(
                    cause = IllegalStateException("nope"),
                    kind = SyncFailureKind.SERVER,
                ),
            ),
            syncScheduler = FakeSyncScheduler(),
        )

        val result = delegate.run()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `first network failure schedules exactly one retry`() = runTest {
        val syncScheduler = FakeSyncScheduler()
        val delegate = DailySyncWorkerDelegate(
            syncCoordinator = FakeSyncCoordinator(
                outcome = SyncOutcome.Failed(
                    cause = java.io.IOException("offline"),
                    kind = SyncFailureKind.NETWORK,
                ),
            ),
            syncScheduler = syncScheduler,
        )

        val result = delegate.run(isNetworkRetry = false)

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf(Duration.ofMinutes(2)), syncScheduler.networkRetryDelays)
    }

    @Test
    fun `network retry failure does not schedule another retry`() = runTest {
        val syncScheduler = FakeSyncScheduler()
        val delegate = DailySyncWorkerDelegate(
            syncCoordinator = FakeSyncCoordinator(
                outcome = SyncOutcome.Failed(
                    cause = java.io.IOException("offline"),
                    kind = SyncFailureKind.NETWORK,
                ),
            ),
            syncScheduler = syncScheduler,
        )

        val result = delegate.run(isNetworkRetry = true)

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(syncScheduler.networkRetryDelays.isEmpty())
    }

    private class FakeSyncCoordinator(
        private val outcome: SyncOutcome,
    ) : SyncCoordinator {
        override suspend fun runSync(trigger: SyncTrigger): SyncOutcome = outcome
    }

    private class FakeSyncScheduler : SyncScheduler {
        val networkRetryDelays = mutableListOf<Duration>()

        override fun enqueueImmediateSync() = Unit

        override fun scheduleNetworkRetry(delay: Duration) {
            networkRetryDelays += delay
        }

        override fun replaceAutoSync(at: Instant) = Unit

        override fun scheduleNextAutoSync(at: Instant) = Unit

        override fun scheduleNetworkRetryAutoSync(at: Instant) = Unit

        override fun cancelAutoSync() = Unit
    }
}
