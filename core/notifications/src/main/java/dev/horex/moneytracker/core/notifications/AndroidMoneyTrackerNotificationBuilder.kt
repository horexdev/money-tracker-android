package dev.horex.moneytracker.core.notifications

import android.app.Notification
import android.content.Context

class AndroidMoneyTrackerNotificationBuilder(
    context: Context,
    private val smallIconResId: Int,
) {
    private val appContext = context.applicationContext

    fun build(request: MoneyTrackerNotificationRequest): Notification {
        val builder = Notification.Builder(appContext, request.channel.id)
            .setSmallIcon(smallIconResId)
            .setContentTitle(request.title)
            .setContentText(request.body)
            .setStyle(Notification.BigTextStyle().bigText(request.body))
            .setAutoCancel(request.autoCancel)
            .setOngoing(request.ongoing)
            .setShowWhen(request.showWhen)
            .setWhen(request.timestampEpochMillis)
            .setCategory(request.category)

        request.contentIntent?.let {
            builder.setContentIntent(it)
        }

        return builder.build()
    }
}
