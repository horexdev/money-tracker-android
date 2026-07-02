package dev.horex.moneytracker.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class AndroidNotificationChannelRegistrar(
    context: Context,
    private val channels: List<MoneyTrackerNotificationChannel> = MoneyTrackerNotificationChannel.All,
) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    fun ensureNotificationChannels() {
        notificationManager.createNotificationChannels(
            channels.map { channel ->
                NotificationChannel(
                    channel.id,
                    appContext.getString(channel.nameResId),
                    channel.importance,
                ).apply {
                    description = appContext.getString(channel.descriptionResId)
                    setShowBadge(channel.showBadge)
                }
            },
        )
    }
}
