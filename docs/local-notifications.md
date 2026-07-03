# Local Notifications

The notification foundation is local-only. It does not add network permissions, server calls, or remote push dependencies.

## Channels

`core:notifications` owns stable channel ids for local feature slices:

- `money_tracker_budget_alerts`
- `money_tracker_recurring_reminders`
- `money_tracker_goal_milestones`
- `money_tracker_weekly_summary`

The app creates these channels on startup through `AndroidNotificationChannelRegistrar`.

## Permission Flow

`AndroidNotificationPermissionController` treats Android 12L and earlier as permission-free for local notifications. On Android 13 and later it exposes `POST_NOTIFICATIONS` as a runtime permission, tracks whether the app has already requested it, and avoids repeating the first-run prompt after a denial. `MainActivity` requests the runtime permission when it is still needed.

## Builder And Delivery

Budget threshold checks create a `MoneyTrackerNotificationRequest` on `BudgetAlerts`, attach an open-app `PendingIntent` from `AndroidNotificationIntentFactory`, and deliver it through `AndroidMoneyTrackerNotifier`. Delivery returns `PermissionRequired`, `NotificationsDisabled`, or `Delivered` so feature code can avoid treating disabled notifications as failed business logic.

Budget threshold state is stored on the local budget row in `last_notified_percent` and `last_notified_at_epoch_millis`. A new weekly or monthly period treats older state as reset, and the state update is conditional so repeated background runs do not emit duplicate alerts for the same threshold period.

Savings goal milestone checks create a `MoneyTrackerNotificationRequest` on `GoalMilestones` only after a local deposit moves a goal across a 25%, 50%, 75%, or 100% milestone. Goal milestone alerts use the existing `notify_goal_milestones` profile preference, which defaults to disabled. There is no background rescan for goal milestones because the current Room schema has no persisted per-goal milestone state; duplicate prevention uses the highest balance inferred from the goal's saved transaction history.
