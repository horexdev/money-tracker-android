package dev.horex.moneytracker.core.savings

import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationChannel
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationDeliveryResult
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalMilestoneNotificationProcessorTest {
    @Test
    fun runDeliversHighestCrossedMilestone() {
        val notifier = RecordingNotifier()
        val processor = processor(notifier)

        val result = processor.run(
            goal = goal(currentCents = 7_600),
            previousCurrentCents = 2_000,
            goalMilestoneNotificationsEnabled = true,
        )

        assertEquals(3, result.milestonesFound)
        assertEquals(1, result.notificationsDelivered)
        assertEquals(0, result.notificationsSuppressed)
        assertEquals(1, notifier.requests.size)
        assertEquals(MoneyTrackerNotificationChannel.GoalMilestones, notifier.requests.single().channel)
        assertEquals("Trip reached 75%", notifier.requests.single().title)
        assertTrue(notifier.requests.single().body.contains("USD 76.00 of USD 100.00"))
        assertEquals(NOW, notifier.requests.single().timestampEpochMillis)
    }

    @Test
    fun runSkipsWhenProfileNotificationsAreDisabled() {
        val notifier = RecordingNotifier()
        val processor = processor(notifier)

        val result = processor.run(
            goal = goal(currentCents = 10_000),
            previousCurrentCents = 0,
            goalMilestoneNotificationsEnabled = false,
        )

        assertEquals(0, result.milestonesFound)
        assertEquals(0, result.notificationsDelivered)
        assertEquals(0, result.notificationsSuppressed)
        assertEquals(emptyList<MoneyTrackerNotificationRequest>(), notifier.requests)
    }

    @Test
    fun runDoesNotDuplicateAlreadyCrossedMilestone() {
        val notifier = RecordingNotifier()
        val processor = processor(notifier)

        val result = processor.run(
            goal = goal(currentCents = 8_000),
            previousCurrentCents = 7_500,
            goalMilestoneNotificationsEnabled = true,
        )

        assertEquals(0, result.milestonesFound)
        assertEquals(0, result.notificationsDelivered)
        assertEquals(0, result.notificationsSuppressed)
        assertEquals(emptyList<MoneyTrackerNotificationRequest>(), notifier.requests)
    }

    @Test
    fun runCountsSuppressedDeliveryAsHandled() {
        val notifier = RecordingNotifier(MoneyTrackerNotificationDeliveryResult.PermissionRequired)
        val processor = processor(notifier)

        val result = processor.run(
            goal = goal(currentCents = 2_500),
            previousCurrentCents = 0,
            goalMilestoneNotificationsEnabled = true,
        )

        assertEquals(1, result.milestonesFound)
        assertEquals(0, result.notificationsDelivered)
        assertEquals(1, result.notificationsSuppressed)
        assertEquals(1, notifier.requests.size)
    }

    private fun processor(notifier: RecordingNotifier): GoalMilestoneNotificationProcessor {
        return GoalMilestoneNotificationProcessor(
            notifier = notifier,
            clock = { NOW },
        )
    }

    private class RecordingNotifier(
        private val result: MoneyTrackerNotificationDeliveryResult =
            MoneyTrackerNotificationDeliveryResult.Delivered,
    ) : GoalMilestoneNotifier {
        val requests = mutableListOf<MoneyTrackerNotificationRequest>()

        override fun notify(request: MoneyTrackerNotificationRequest): MoneyTrackerNotificationDeliveryResult {
            requests += request
            return result
        }
    }

    private fun goal(currentCents: Long): SavingsGoal {
        return SavingsGoal(
            id = 7,
            profileId = 1,
            name = "Trip",
            targetCents = 10_000,
            currentCents = currentCents,
            currencyCode = "USD",
            deadlineDate = null,
            accountId = null,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
    }

    private companion object {
        const val NOW = 1_783_036_800_000L
    }
}
