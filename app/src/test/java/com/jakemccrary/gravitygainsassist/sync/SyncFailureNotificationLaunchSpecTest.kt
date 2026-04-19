package com.jakemccrary.gravitygainsassist.sync

import android.content.Intent
import com.jakemccrary.gravitygainsassist.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncFailureNotificationLaunchSpecTest {
    @Test
    fun `sync failure notification launches the main activity`() {
        assertEquals(MainActivity::class.java, SyncFailureNotificationLaunchSpec.activityClass)
    }

    @Test
    fun `sync failure notification reuses the app task when launched`() {
        assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            SyncFailureNotificationLaunchSpec.intentFlags,
        )
    }
}
