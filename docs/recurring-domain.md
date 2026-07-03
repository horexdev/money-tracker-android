# Recurring Domain

`core:recurring` owns local recurring transaction templates and due processing.

## Behavior

- CRUD and toggle operations are profile-scoped.
- Account currency is taken from the selected local account when a template is created, updated, or processed.
- Categories must be active and compatible with the recurring transaction type.
- Frequencies are `daily`, `weekly`, `monthly`, and `yearly`.
- If a template has no explicit next run on creation, the next run is calculated from the current clock.
- Due processing creates one local transaction per active due template and advances the next run relative to the processing time.

## Idempotency

Due processing claims `(profile_id, recurring_transaction_id, scheduled_for_epoch_millis)` in `recurring_transaction_runs`
before writing the generated transaction. The marker and transaction are written in the same Room transaction.

If a repeated catch-up sees an already claimed run, it skips transaction creation and advances the template past the stale run.
This keeps app-open catch-up and periodic WorkManager attempts from duplicating local transactions.
