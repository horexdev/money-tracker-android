package dev.horex.moneytracker.core.savings

import android.app.PendingIntent
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationChannel
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationDeliveryResult
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationRequest
import kotlin.math.absoluteValue

fun interface GoalMilestoneNotificationPreferenceProvider {
    suspend fun areGoalMilestoneNotificationsEnabled(profileId: Long): Boolean
}

fun interface GoalMilestoneNotifier {
    fun notify(request: MoneyTrackerNotificationRequest): MoneyTrackerNotificationDeliveryResult
}

data class GoalMilestoneNotificationRunResult(
    val milestonesFound: Int,
    val notificationsDelivered: Int,
    val notificationsSuppressed: Int,
)

class GoalMilestoneNotificationProcessor(
    private val notifier: GoalMilestoneNotifier,
    private val contentIntentFactory: (SavingsGoal, Int) -> PendingIntent? = { _, _ -> null },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun run(
        goal: SavingsGoal,
        previousCurrentCents: Long,
        goalMilestoneNotificationsEnabled: Boolean,
    ): GoalMilestoneNotificationRunResult {
        if (!goalMilestoneNotificationsEnabled) {
            return GoalMilestoneNotificationRunResult(
                milestonesFound = 0,
                notificationsDelivered = 0,
                notificationsSuppressed = 0,
            )
        }

        val milestones = goal.crossedMilestones(previousCurrentCents)
        val milestone = milestones.lastOrNull()
            ?: return GoalMilestoneNotificationRunResult(
                milestonesFound = 0,
                notificationsDelivered = 0,
                notificationsSuppressed = 0,
            )

        val request = goal.toNotificationRequest(milestone, clock())
        return when (notifier.notify(request)) {
            MoneyTrackerNotificationDeliveryResult.Delivered -> GoalMilestoneNotificationRunResult(
                milestonesFound = milestones.size,
                notificationsDelivered = 1,
                notificationsSuppressed = 0,
            )
            MoneyTrackerNotificationDeliveryResult.PermissionRequired,
            MoneyTrackerNotificationDeliveryResult.NotificationsDisabled -> GoalMilestoneNotificationRunResult(
                milestonesFound = milestones.size,
                notificationsDelivered = 0,
                notificationsSuppressed = 1,
            )
        }
    }

    private fun SavingsGoal.toNotificationRequest(
        milestonePercent: Int,
        nowEpochMillis: Long,
    ): MoneyTrackerNotificationRequest {
        return MoneyTrackerNotificationRequest(
            notificationId = stableGoalMilestoneNotificationId(),
            channel = MoneyTrackerNotificationChannel.GoalMilestones,
            title = "$name reached $milestonePercent%",
            body = buildGoalMilestoneBody(milestonePercent),
            contentIntent = contentIntentFactory(this, milestonePercent),
            timestampEpochMillis = nowEpochMillis,
        )
    }
}

private fun SavingsGoal.buildGoalMilestoneBody(milestonePercent: Int): String {
    return "$name progress reached $milestonePercent%: " +
        "${formatMoney(currentCents, currencyCode)} of ${formatMoney(targetCents, currencyCode)}."
}

private fun SavingsGoal.stableGoalMilestoneNotificationId(): Int {
    var result = 43
    result = 31 * result + profileId.hashCode()
    result = 31 * result + id.hashCode()
    return result.absoluteValue.takeIf { it > 0 } ?: 1
}

private fun formatMoney(
    amountCents: Long,
    currencyCode: String,
): String {
    val absoluteCents = amountCents.absoluteValue
    val major = absoluteCents / 100
    val minor = absoluteCents % 100
    val sign = if (amountCents < 0) "-" else ""

    return "$sign$currencyCode $major.${minor.toString().padStart(2, '0')}"
}
