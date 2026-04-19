package com.jakemccrary.gravitygainsassist.sync

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jakemccrary.gravitygainsassist.MainActivity
import com.jakemccrary.gravitygainsassist.NotificationPermissionPolicy
import com.jakemccrary.gravitygainsassist.R
import java.util.concurrent.atomic.AtomicInteger

interface SyncFailureNotifier {
    fun notifyFailure(message: String)

    fun notifySuccess(message: String)
}

object NoOpSyncFailureNotifier : SyncFailureNotifier {
    override fun notifyFailure(message: String) = Unit

    override fun notifySuccess(message: String) = Unit
}

class AndroidSyncFailureNotifier(
    context: Context,
) : SyncFailureNotifier {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    override fun notifyFailure(message: String) {
        ensureChannel()
        if (!notificationsAllowed()) {
            return
        }

        notifyIfAllowed(
            title = appContext.getString(R.string.sync_failure_notification_title),
            message = message,
            icon = android.R.drawable.stat_notify_error,
        )
    }

    override fun notifySuccess(message: String) {
        ensureChannel()
        if (!notificationsAllowed()) {
            return
        }

        notifyIfAllowed(
            title = appContext.getString(R.string.sync_success_notification_title),
            message = message,
            icon = android.R.drawable.stat_sys_upload_done,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val existingChannel = manager.getNotificationChannel(CHANNEL_ID)
        if (existingChannel != null) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.sync_failure_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description =
                    appContext.getString(R.string.sync_failure_notification_channel_description)
            },
        )
    }

    private fun notificationsAllowed(): Boolean {
        return NotificationPermissionPolicy.canPostNotifications(
            sdkInt = Build.VERSION.SDK_INT,
            permissionGranted = hasPostNotificationsPermission(),
        )
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            NotificationPermissionPolicy.POST_NOTIFICATIONS_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(
        title: String,
        message: String,
        icon: Int,
    ) {
        try {
            notificationManager.notify(
                nextNotificationId(),
                NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(icon)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(contentIntent())
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build(),
            )
        } catch (_: SecurityException) {
            return
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(appContext, SyncFailureNotificationLaunchSpec.activityClass)
            .setFlags(SyncFailureNotificationLaunchSpec.intentFlags)
        return PendingIntent.getActivity(
            appContext,
            SyncFailureNotificationLaunchSpec.requestCode,
            intent,
            SyncFailureNotificationLaunchSpec.pendingIntentFlags,
        )
    }

    private fun nextNotificationId(): Int = notificationIds.incrementAndGet()

    private companion object {
        const val CHANNEL_ID = "sync_failures"
        val notificationIds = AtomicInteger(1000)
    }
}

internal object SyncFailureNotificationLaunchSpec {
    val activityClass: Class<*> = MainActivity::class.java
    const val intentFlags: Int = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    const val requestCode: Int = 3001
    const val pendingIntentFlags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
