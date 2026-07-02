# Transactions Domain

`core:transactions` owns ordinary income/expense transactions for the Android offline app.

## Contract

- Amounts are stored as positive integer cents in `Long`.
- Transaction currency is derived from the selected account and is not accepted from UI input.
- `snapshot_date` is assigned only when a transaction is created, using the UTC date of `createdAtEpochMillis` or the current clock value.
- Edit operations update amount, category, note, and `created_at_epoch_millis`; they do not rewrite `snapshot_date`, account, type, or currency.
- History/list queries exclude `is_adjustment = true` rows.
- Ordinary transaction categories must be active and usable for the transaction type: `both`, `expense`, or `income`.
- Adjustment transactions are immutable through this contract. MT-C03 owns adjustment creation rules.
- Transfer-linked transactions are not edited or deleted directly. MT-C04 owns linked transfer mutation rules.
- Pagination follows the Mini App defaults: page size defaults to 20 when outside `1..100`, empty result sets still report one page.
- `core:transactions` does not store or expose Telegram/source/server identity fields.
