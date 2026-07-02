package dev.horex.moneytracker.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTrackerNotificationRequestTest {
    @Test
    fun requestRequiresPositiveNotificationId() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyTrackerNotificationRequest(
                notificationId = 0,
                channel = MoneyTrackerNotificationChannel.BudgetAlerts,
                title = "Budget",
                body = "Limit reached",
            )
        }
    }

    @Test
    fun requestRequiresVisibleContent() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyTrackerNotificationRequest(
                notificationId = 1,
                channel = MoneyTrackerNotificationChannel.BudgetAlerts,
                title = " ",
                body = "Limit reached",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyTrackerNotificationRequest(
                notificationId = 1,
                channel = MoneyTrackerNotificationChannel.BudgetAlerts,
                title = "Budget",
                body = " ",
            )
        }
    }

    @Test
    fun requestDefaultsAreSuitableForLocalReminders() {
        val request = MoneyTrackerNotificationRequest(
            notificationId = 42,
            channel = MoneyTrackerNotificationChannel.RecurringReminders,
            title = "Upcoming",
            body = "Rent is due tomorrow",
            timestampEpochMillis = 123L,
        )

        assertEquals(42, request.notificationId)
        assertEquals(MoneyTrackerNotificationChannel.RecurringReminders, request.channel)
        assertEquals(true, request.autoCancel)
        assertEquals(false, request.ongoing)
        assertEquals(true, request.showWhen)
        assertEquals(123L, request.timestampEpochMillis)
        assertEquals("reminder", request.category)
    }
}
