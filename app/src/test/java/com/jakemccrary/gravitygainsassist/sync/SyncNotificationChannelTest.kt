package com.jakemccrary.gravitygainsassist.sync

import com.jakemccrary.gravitygainsassist.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyncNotificationChannelTest {
    @Test
    fun `sync success and failure notifications use distinct channels`() {
        assertEquals("sync_failures", SyncNotificationChannel.FAILURE.id)
        assertEquals("sync_successes", SyncNotificationChannel.SUCCESS.id)
        assertNotEquals(
            SyncNotificationChannel.FAILURE.id,
            SyncNotificationChannel.SUCCESS.id,
        )
    }

    @Test
    fun `all sync notification channels can be registered`() {
        assertEquals(
            listOf(SyncNotificationChannel.FAILURE, SyncNotificationChannel.SUCCESS),
            SyncNotificationChannel.all,
        )
    }

    @Test
    fun `sync notification channels have separate user visible labels`() {
        assertEquals(
            R.string.sync_failure_notification_channel_name,
            SyncNotificationChannel.FAILURE.nameResId,
        )
        assertEquals(
            R.string.sync_success_notification_channel_name,
            SyncNotificationChannel.SUCCESS.nameResId,
        )
    }
}
