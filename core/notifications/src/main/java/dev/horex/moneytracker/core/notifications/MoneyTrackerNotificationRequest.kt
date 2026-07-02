package dev.horex.moneytracker.core.notifications

import android.app.PendingIntent

data class MoneyTrackerNotificationRequest(
    val notificationId: Int,
    val channel: MoneyTrackerNotificationChannel,
    val title: String,
    val body: String,
    val contentIntent: PendingIntent? = null,
    val autoCancel: Boolean = true,
    val ongoing: Boolean = false,
    val showWhen: Boolean = true,
    val timestampEpochMillis: Long = System.currentTimeMillis(),
    val category: String = NOTIFICATION_CATEGORY_REMINDER,
) {
    init {
        require(notificationId > 0) { "Notification id must be positive." }
        require(title.isNotBlank()) { "Notification title must not be blank." }
        require(body.isNotBlank()) { "Notification body must not be blank." }
    }

    private companion object {
        const val NOTIFICATION_CATEGORY_REMINDER = "reminder"
    }
}
