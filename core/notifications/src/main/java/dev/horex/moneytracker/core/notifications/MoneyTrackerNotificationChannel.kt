package dev.horex.moneytracker.core.notifications

import android.app.NotificationManager

enum class MoneyTrackerNotificationChannel(
    val id: String,
    val nameResId: Int,
    val descriptionResId: Int,
    val importance: Int,
    val showBadge: Boolean = true,
) {
    BudgetAlerts(
        id = "money_tracker_budget_alerts",
        nameResId = R.string.notification_channel_budget_alerts_name,
        descriptionResId = R.string.notification_channel_budget_alerts_description,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
    ),
    RecurringReminders(
        id = "money_tracker_recurring_reminders",
        nameResId = R.string.notification_channel_recurring_reminders_name,
        descriptionResId = R.string.notification_channel_recurring_reminders_description,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
    ),
    GoalMilestones(
        id = "money_tracker_goal_milestones",
        nameResId = R.string.notification_channel_goal_milestones_name,
        descriptionResId = R.string.notification_channel_goal_milestones_description,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
    ),
    WeeklySummary(
        id = "money_tracker_weekly_summary",
        nameResId = R.string.notification_channel_weekly_summary_name,
        descriptionResId = R.string.notification_channel_weekly_summary_description,
        importance = NotificationManager.IMPORTANCE_LOW,
        showBadge = false,
    ),
    ;

    companion object {
        val All: List<MoneyTrackerNotificationChannel> = entries.toList()

        fun fromId(id: String): MoneyTrackerNotificationChannel? {
            return entries.firstOrNull { it.id == id }
        }
    }
}
