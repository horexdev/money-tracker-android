package dev.horex.moneytracker.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MoneyTrackerNotificationChannelTest {
    @Test
    fun foundationDefinesStableFutureFeatureChannels() {
        assertEquals(
            listOf(
                "money_tracker_budget_alerts",
                "money_tracker_recurring_reminders",
                "money_tracker_goal_milestones",
                "money_tracker_weekly_summary",
            ),
            MoneyTrackerNotificationChannel.All.map { it.id },
        )
    }

    @Test
    fun channelLookupUsesStableIds() {
        MoneyTrackerNotificationChannel.All.forEach { channel ->
            assertEquals(channel, MoneyTrackerNotificationChannel.fromId(channel.id))
        }
        assertEquals(null, MoneyTrackerNotificationChannel.fromId("unknown"))
    }

    @Test
    fun allChannelsHaveResources() {
        MoneyTrackerNotificationChannel.All.forEach { channel ->
            assertNotNull(channel.nameResId)
            assertNotNull(channel.descriptionResId)
        }
    }
}
