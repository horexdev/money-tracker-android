# Background Work Foundation

`core:background` owns the WorkManager foundation for future local sync, backup, and notification slices.

## Scope

- Work is scheduled through WorkManager, not exact alarms.
- Periodic work specs use unique names and `ExistingPeriodicWorkPolicy.KEEP` by default.
- Test hooks use one-off unique work and `ExistingWorkPolicy.REPLACE` by default.
- Foundation workers are local-only: `PeriodicBackgroundWorkSpec` rejects constraints that require network.
- The module does not implement reminder, sync, backup, or notification jobs.

## Domain Hooks

Future domain layers register handlers in `MutableBackgroundTaskRegistry` by `BackgroundTaskId`. A worker run receives `BackgroundTaskRunContext`, including:

- `taskId`
- `trigger` (`Periodic` or `Test`)
- `attemptNumber`
- `startedAtEpochMillis`
- `idempotencyGuard`

`BackgroundTaskIdempotencyGuard` is intentionally domain-facing. Domain implementations can claim, complete, or record retryable/permanent failures for keys such as a daily backup window or a notification batch. The default guard is permissive and stores nothing, so no database schema is introduced in this foundation task.

## App Wiring

`MoneyTrackerApplication` provides WorkManager configuration with `MoneyTrackerWorkerFactory`. `MoneyTrackerAppContainer` owns the registry, executor, worker factory, and scheduler instances so future feature slices can register handlers without changing the WorkManager bootstrap.
