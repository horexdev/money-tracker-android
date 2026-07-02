# MoneyTrackerBackup V1

## Scope

`core:backup` owns the JSON DTO contract for `MoneyTrackerBackup` version `1`.
The contract is shared by a future source-side exporter and the Android importer,
but this module does not implement either data extraction or restore execution.

The backup format is intentionally separate from Room entities. It mirrors the
Android offline data surface and stores only values needed to rebuild local
records.

## Top-Level Shape

Every file contains:

- `format`: always `MoneyTrackerBackup`;
- `version`: always `1`;
- `created_at_epoch_millis`: backup creation timestamp;
- `profiles`: exported local profile graphs;
- `exchange_rate_snapshots`: global date/currency rate snapshots.

The empty golden fixture is tracked at
`core/backup/src/test/resources/fixtures/money_tracker_backup_v1_empty.json`.

## Identity Boundary

The DTO must not contain Telegram, server, source database, Room, `legacy_*`,
`initData`, bot, chat, or admin identity fields. It does not expose numeric
primary keys such as `id`, `user_id`, `account_id`, or `category_id`.

All relationships are expressed with export-local refs:

- every profile row uses `ref`;
- account links use `account_ref`, `from_account_ref`, and `to_account_ref`;
- category links use `category_ref`;
- transaction links use `transaction_ref`, `from_transaction_ref`, and
  `to_transaction_ref`;
- savings links use `goal_ref`;
- profile-contained rows such as budgets, recurring transactions, goal
  transactions, exchange rate overrides, and transaction templates also carry
  their own `ref`.

Refs are opaque strings valid only inside a single backup file. A producer may
generate sequential refs, but they must be generated for the export and must not
be copied raw from source database or Room primary keys.

## Contract Checks

`MoneyTrackerBackupV1Contract.requireValidExportRefs()` performs only format
and relationship checks:

- `format` and `version` must match v1;
- refs must be non-blank, lowercase, URL-safe-ish tokens such as
  `account:main` or `transaction:lunch-1`;
- refs must not include `telegram`, `source`, `legacy`, `init_data`, `bot`, or
  `chat`;
- refs are unique within their collection;
- all relationship refs resolve within the same profile.

Business validation such as required default accounts, positive amounts, date
windows, import conflict handling, and restore behavior belongs to follow-up
validation/import tasks.

## Domain Coverage

V1 covers the current Android offline domains:

- local profile settings and UI preferences;
- accounts;
- categories, including protected transfer/adjustment categories;
- ordinary, adjustment, and transfer-linked transactions;
- transfers with fixed-point `exchange_rate_e8`;
- budgets with notification state;
- recurring transactions;
- savings goals and goal transactions;
- global exchange rate snapshots and profile-owned rate overrides;
- transaction templates.

Amounts are integer cents. Currency conversion rates are fixed-point `rate_e8`,
where `100000000` means `1.0`. Date-only fields use `YYYY-MM-DD` strings.
Timestamps use epoch milliseconds.
