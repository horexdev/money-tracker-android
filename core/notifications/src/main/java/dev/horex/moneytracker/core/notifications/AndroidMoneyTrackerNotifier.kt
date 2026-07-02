package dev.horex.moneytracker.core.notifications

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context

class AndroidMoneyTrackerNotifier(
    context: Context,
    private val channelRegistrar: AndroidNotificationChannelRegistrar,
    private val permissionController: AndroidNotificationPermissionController,
    private val notificationBuilder: AndroidMoneyTrackerNotificationBuilder,
) {
    private val notificationManager = context.applicationContext
        .getSystemService(NotificationManager::class.java)

    fun notify(request: MoneyTrackerNotificationRequest): MoneyTrackerNotificationDeliveryResult {
        if (!permissionController.canPostNotifications()) {
            return MoneyTrackerNotificationDeliveryResult.PermissionRequired
        }
        if (!notificationManager.areNotificationsEnabled()) {
            return MoneyTrackerNotificationDeliveryResult.NotificationsDisabled
        }

        channelRegistrar.ensureNotificationChannels()
        postNotification(request)
        return MoneyTrackerNotificationDeliveryResult.Delivered
    }

    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(request: MoneyTrackerNotificationRequest) {
        notificationManager.notify(
            request.notificationId,
            notificationBuilder.build(request),
        )
    }
}
