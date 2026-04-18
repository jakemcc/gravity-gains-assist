package com.jakemccrary.gravitygainsassist

object NotificationPermissionPolicy {
    const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

    fun shouldRequestPostNotifications(
        sdkInt: Int,
        permissionGranted: Boolean,
    ): Boolean {
        return sdkInt >= POST_NOTIFICATIONS_RUNTIME_PERMISSION_SDK && !permissionGranted
    }

    fun canPostNotifications(
        sdkInt: Int,
        permissionGranted: Boolean,
    ): Boolean {
        return sdkInt < POST_NOTIFICATIONS_RUNTIME_PERMISSION_SDK || permissionGranted
    }

    private const val POST_NOTIFICATIONS_RUNTIME_PERMISSION_SDK = 33
}
