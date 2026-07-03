# MoneyTrackerBackup V1

## Scope

`core:backup` owns the JSON DTO contract for `MoneyTrackerBackup` version `1`,
the Android exporter that extracts a portable snapshot from Room, the Android
importer that restores validated backup data into the local Room database, SAF
document read/write orchestration, and the optional password-encrypted backup
container. Full import/export UI is tracked separately.

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

The anonymized full-domain migration fixture is tracked at
`core/backup/src/test/resources/fixtures/money_tracker_backup_v1_anonymized_full.json`.
It uses export-local refs such as `profile:p000001` and `account:a000001`
that mirror the source-side exporter shape without copying source database IDs.
The fixture covers profile preferences, accounts, categories, transactions,
transfers, budgets, recurring transactions, savings goals, goal transactions,
exchange rate snapshots, exchange rate overrides, and transaction templates.

`MoneyTrackerBackupMigrationFixturesTest` keeps this fixture golden by asserting:

- decode -> encode output matches the checked-in JSON after line-ending
  normalization;
- decode -> encode -> decode returns the same DTO graph;
- `MoneyTrackerBackupV1Validator.validateJson()` reports no warnings or errors;
- importing the fixture remaps relationship refs to generated local IDs rather
  than persisting export-local refs;
- the raw JSON contains no Telegram/source identity keys or values, source
  database IDs, `legacy_*` markers, init data, bot/chat metadata, SQLCipher
  passphrases, Android Keystore material, or device-bound secrets.

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

## Android Exporter

`MoneyTrackerBackupExporter` reads the current Room data through backup-specific
DAO methods and builds a `MoneyTrackerBackup` DTO without changing the DTO/API
contract. Reads run inside one Room transaction so profiles, child rows, and
global exchange-rate snapshots come from a consistent local snapshot.

Export refs are generated in memory for the current file only. They are stable
for deterministic row order, for example `profile:1`, `account:1`, and
`transaction:1`, but they do not contain Room primary keys and are never stored
back into the database.

The exporter does not read or serialize the SQLCipher passphrase, Android
Keystore aliases, app-private preferences that hold device-bound secrets, or any
Telegram/source identity fields. Portable backup encryption must use a separate
backup key flow.

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

## Dry-Run Validation

`MoneyTrackerBackupV1Validator` is the deterministic dry-run gate for future
restore flows. It has no Room, repository, SAF, or transaction dependency and
does not write data. Restore code must run it before opening a DB write
transaction.

The validator returns `MoneyTrackerBackupValidationResult` with stable
`errors`, `warnings`, and `canImport` fields:

- `Error` issues block import;
- `Warning` issues document non-blocking dry-run findings;
- issue ordering is deterministic by severity, JSON path, code, and message.

`validate(backup)` validates an already decoded DTO. `validateJson(value)` first
scans the raw JSON tree for source identity fields and then validates the DTO
shape and relationship graph. This raw scan blocks Telegram/source identity
fields even when they are not part of the DTO contract, including:

- Telegram user/profile fields such as `telegram_id`, `username`,
  `first_name`, `last_name`, `initData`, and `init_data`;
- bot/chat metadata such as `bot_id` and `chat_id`;
- source or legacy markers such as `source_id`, `source_db_id`, and
  `legacy_id`;
- source database identifiers such as `id`, `user_id`, `account_id`,
  `category_id`, `from_tx_id`, or any other `*_id` field.

Reference validation is complete before restore execution. The dry-run checks
profile, account, category, transaction, transfer, budget, recurring
transaction, savings goal, goal transaction, exchange rate override, and
transaction template refs for valid syntax, forbidden ref parts, duplicates, and
unresolved relationship refs.

## Android Importer

`MoneyTrackerBackupImporter` imports a decoded `MoneyTrackerBackup` into
`MoneyTrackerDatabase`. It always runs `MoneyTrackerBackupV1Validator` before
opening a write transaction. Validation errors fail before any DB write.

The importer accepts `MoneyTrackerBackupImportOptions.selectedProfileRefs` for
profile-level selection. `null` imports every profile in backup order. A non-null
set imports only matching profile refs, fails if any selected ref is missing,
and imports no profiles for an empty set.

All writes for a restore attempt are wrapped in one Room `withTransaction`
block. If any profile row or child row fails to persist, Room rolls back profile
rows, child rows, and top-level exchange-rate snapshots written earlier in the
same attempt.

Export-local refs are used only as in-memory lookup keys while the transaction
is running. The importer stores new local Room IDs in relationship columns such
as `account_id`, `category_id`, `transaction_id`, and `goal_id`; it does not
persist backup refs, source database IDs, Telegram identifiers, `legacy_*`
fields, or source metadata.

## SAF Document Flow

`MoneyTrackerBackupSafRepository` writes export JSON to a caller-provided SAF
`Uri` through `ContentResolver.openOutputStream(uri, "wt")` and restores from a
SAF `Uri` through `ContentResolver.openInputStream(uri)`. Restore runs
`MoneyTrackerBackupV1Validator.validateJson()` on the raw document before
decoding so forbidden unknown identity fields are rejected before DB writes.

Plain v1 JSON stays backward compatible. `restoreFromDocument(uri)` imports only
plain `MoneyTrackerBackup` JSON; when the document is an encrypted container it
fails with `MoneyTrackerBackupPasswordRequiredException` so callers can ask for a
password and retry through `restoreEncryptedFromDocument()`.

## Password Encryption Container

Password-protected backups wrap the unchanged `MoneyTrackerBackup` v1 JSON
payload in a `MoneyTrackerBackupEncryptedContainer` JSON document. The wrapper
stores only portable crypto metadata:

- `format`: `MoneyTrackerBackupEncryptedContainer`;
- `version`: container version `1`;
- `payload_format`: `MoneyTrackerBackup`;
- `payload_version`: `1`;
- `payload_encoding`: `utf-8-json`;
- `encryption.algorithm`: `AES-256-GCM`;
- `encryption.kdf.algorithm`: `PBKDF2-HMAC-SHA256`;
- `encryption.kdf.iterations`: PBKDF2 iteration count used for this file;
- `encryption.kdf.salt`: Base64-encoded per-file salt;
- `encryption.kdf.key_length_bits`: `256`;
- `encryption.nonce`: Base64-encoded per-file AES-GCM nonce;
- `encryption.ciphertext`: Base64-encoded ciphertext with the GCM tag appended.

The password is never serialized. The SQLCipher passphrase, Android Keystore
alias, and any device-bound database secret are not inputs to backup encryption
and are never exported. Container metadata is authenticated with AES-GCM AAD, so
changing KDF parameters, payload contract fields, or nonce makes decryption fail.

After password decryption, restore still runs
`MoneyTrackerBackupV1Validator.validateJson()` on the decrypted payload before
opening a DB write transaction. This preserves the same Telegram/source identity
field ban for encrypted backups as for plain backups. A wrong password or a
tampered encrypted payload fails with `MoneyTrackerBackupWrongPasswordException`
and does not start an import transaction.

The app module provides reusable Activity Result hooks for
`Intent.ACTION_CREATE_DOCUMENT` and `Intent.ACTION_OPEN_DOCUMENT`. They return a
selected `Uri` to the caller; the full user-facing import/export screen is not
part of this slice.

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
