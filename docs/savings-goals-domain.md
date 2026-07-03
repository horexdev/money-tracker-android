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

## Migration Boundary

The Android domain stores only local profile, account, category, transaction, and goal IDs. It does not add or expose migrated identity metadata from the previous platform.
