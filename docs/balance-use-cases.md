# Balance Use Cases

`core:balance` owns derived balance snapshots for the Android offline app.

## Contract

- Balances are derived from the local transaction ledger; no account balance is stored.
- Account balances include ordinary transactions, transfer-linked transactions, and hidden balance adjustments.
- History/statistics visibility flags do not remove ledger rows from balance calculations.
- `includeInTotal = false` keeps an account visible in account balances but excludes it from aggregate `byCurrency` and `totalInBaseCents` unless the caller explicitly opts in or asks for that account directly.
- `hideAmounts` is a presentation preference and is not read by balance use cases.
- Base currency defaults to the default account currency and falls back to `USD` when no default account exists.
- Cross-currency totals convert each ledger row using the row `snapshotDate` and `core:currency` rates.
- Display conversions convert `totalInBaseCents` into requested display currencies using `core:currency`.
- `core:balance` does not store or expose Telegram/source/server identity fields.
