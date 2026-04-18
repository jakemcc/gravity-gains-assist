package com.jakemccrary.gravitygainsassist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test
    fun `does not request runtime notification permission before api 33`() {
        assertFalse(
            NotificationPermissionPolicy.shouldRequestPostNotifications(
                sdkInt = 32,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `requests runtime notification permission on api 33 and newer when missing`() {
        assertTrue(
            NotificationPermissionPolicy.shouldRequestPostNotifications(
                sdkInt = 33,
                permissionGranted = false,
            ),
        )
        assertTrue(
            NotificationPermissionPolicy.shouldRequestPostNotifications(
                sdkInt = 36,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `does not request runtime notification permission when already granted`() {
        assertFalse(
            NotificationPermissionPolicy.shouldRequestPostNotifications(
                sdkInt = 33,
                permissionGranted = true,
            ),
        )
    }

    @Test
    fun `allows notifications before api 33 without runtime permission`() {
        assertTrue(
            NotificationPermissionPolicy.canPostNotifications(
                sdkInt = 32,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `requires runtime notification permission on api 33 and newer`() {
        assertFalse(
            NotificationPermissionPolicy.canPostNotifications(
                sdkInt = 33,
                permissionGranted = false,
            ),
        )
        assertTrue(
            NotificationPermissionPolicy.canPostNotifications(
                sdkInt = 33,
                permissionGranted = true,
            ),
        )
    }
}
