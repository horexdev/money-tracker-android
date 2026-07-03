# Savings Goals Domain

`core:savings` owns local savings goal CRUD, deposit/withdraw operations, and goal history.

## Model

- Goals are profile-scoped rows in `savings_goals`.
- History entries are profile-scoped `goal_transactions` rows with type `deposit` or `withdraw`.
- Public reads derive current progress from history; repository deposit and withdraw also synchronize the stored `current_cents` column.
- Goal progress is capped at `100%`; remaining amount never drops below zero.

## Linked Account Transactions

When a goal has `account_id`, deposits create an expense transaction and withdrawals create an income transaction on that account. Linked transactions use the profile savings category and the linked account currency. The transaction note defaults to the goal name unless the operation passes a non-blank note.

Unlinked goals still record goal history but do not create account ledger transactions.

## Milestone Notifications

Local goal milestone notifications are opt-in through the profile `notify_goal_milestones` preference. Deposits compare the highest balance already present in the goal's transaction history with the updated goal progress and emit at most one local `GoalMilestones` notification for the highest crossed 25%, 50%, 75%, or 100% milestone. Withdrawals and repeated operations that do not cross a new all-time milestone do not notify.

Milestone notification dedupe does not add Room columns. It relies on saved goal transaction history, so there is no background goal milestone scan until a future schema can persist per-goal milestone notification state.

## Migration Boundary

The Android domain stores only local profile, account, category, transaction, and goal IDs. It does not add or expose migrated identity metadata from the previous platform.
