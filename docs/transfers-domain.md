# Transfers Domain

`core:transfers` owns account-to-account movement records and their linked local ledger rows.

## Contract

- Transfer amount is stored as positive source-account cents.
- Creating a transfer inserts one expense transaction on the source account and one income transaction on the destination account.
- Linked transactions use the protected `transfer` category and cannot be edited or deleted through the ordinary transactions contract.
- `exchangeRateE8` is fixed-point with scale `100_000_000`; the destination amount is truncated integer cents after conversion.
- If an explicit exchange rate is absent, same-currency transfers use `1.0` and cross-currency transfers resolve the rate from `core:currency` for the transfer snapshot date.
- If an explicit exchange rate is non-positive, it falls back to `1.0`, matching the source app transfer behavior.
- Deleting a transfer deletes the transfer record and both linked transactions in one Room transaction.
- Transfer-linked transactions remain in account balances but are excluded from category frequency and future income/expense stats through the protected `transfer` category.
- `core:transfers` does not store or expose Telegram/source/server identity fields.
