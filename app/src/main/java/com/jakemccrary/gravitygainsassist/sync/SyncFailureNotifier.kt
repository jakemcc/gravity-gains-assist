package com.jakemccrary.gravitygainsassist.sync

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
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

internal enum class SyncNotificationChannel(
    val id: String,
    @get:StringRes val nameResId: Int,
    @get:StringRes val descriptionResId: Int,
) {
    FAILURE(
        id = "sync_failures",
        nameResId = R.string.sync_failure_notification_channel_name,
        descriptionResId = R.string.sync_failure_notification_channel_description,
    ),
    SUCCESS(
        id = "sync_successes",
        nameResId = R.string.sync_success_notification_channel_name,
        descriptionResId = R.string.sync_success_notification_channel_description,
    );

    companion object {
        val all: List<SyncNotificationChannel> = entries.toList()
    }
}

class AndroidSyncFailureNotifier(
    context: Context,
) : SyncFailureNotifier {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    override fun notifyFailure(message: String) {
        ensureChannels()
        if (!notificationsAllowed()) {
            return
        }

        notifyIfAllowed(
            channel = SyncNotificationChannel.FAILURE,
            title = appContext.getString(R.string.sync_failure_notification_title),
            message = message,
            icon = android.R.drawable.stat_notify_error,
        )
    }

    override fun notifySuccess(message: String) {
        ensureChannels()
        if (!notificationsAllowed()) {
            return
        }

        notifyIfAllowed(
            channel = SyncNotificationChannel.SUCCESS,
            title = appContext.getString(R.string.sync_success_notification_title),
            message = message,
            icon = android.R.drawable.stat_sys_upload_done,
        )
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        SyncNotificationChannel.all
            .filter { channel -> manager.getNotificationChannel(channel.id) == null }
            .map { channel ->
                NotificationChannel(
                    channel.id,
                    appContext.getString(channel.nameResId),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = appContext.getString(channel.descriptionResId)
                }
            }
            .takeIf { channels -> channels.isNotEmpty() }
            ?.let(manager::createNotificationChannels)
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
        channel: SyncNotificationChannel,
        title: String,
        message: String,
        icon: Int,
    ) {
        try {
            notificationManager.notify(
                nextNotificationId(),
                NotificationCompat.Builder(appContext, channel.id)
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
        val notificationIds = AtomicInteger(1000)
    }
}

internal object SyncFailureNotificationLaunchSpec {
    val activityClass: Class<*> = MainActivity::class.java
    const val intentFlags: Int = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    const val requestCode: Int = 3001
    const val pendingIntentFlags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
