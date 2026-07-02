# Local Notifications

The notification foundation is local-only. It does not add network permissions, server calls, or remote push dependencies.

## Channels

`core:notifications` owns stable channel ids for future feature slices:

- `money_tracker_budget_alerts`
- `money_tracker_recurring_reminders`
- `money_tracker_goal_milestones`
- `money_tracker_weekly_summary`

The app creates these channels on startup through `AndroidNotificationChannelRegistrar`.

## Permission Flow

`AndroidNotificationPermissionController` treats Android 12L and earlier as permission-free for local notifications. On Android 13 and later it exposes `POST_NOTIFICATIONS` as a runtime permission, tracks whether the app has already requested it, and avoids repeating the first-run prompt after a denial. `MainActivity` requests the runtime permission when it is still needed.

## Builder And Delivery

Future budget, recurring, and goal tasks should create a `MoneyTrackerNotificationRequest` with one of the stable channels, optional open-app `PendingIntent` from `AndroidNotificationIntentFactory`, and deliver it through `AndroidMoneyTrackerNotifier`. Delivery returns `PermissionRequired`, `NotificationsDisabled`, or `Delivered` so feature code can avoid treating disabled notifications as failed business logic.
