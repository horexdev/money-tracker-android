# Room Schema V1

## Scope

`core:database` owns the first local Room contract for the Android offline migration.

The schema is based on the current Mini App PostgreSQL migrations, SQL queries, backend services, and web API types. It intentionally adapts server `users` into local Android profiles and does not copy Telegram identity fields or source database identifiers.

Production opens this schema through SQLCipher. The encrypted database open flow and Android Keystore-backed passphrase storage are documented in `docs/database-encryption.md`.

Room DAOs stay inside `core:database`. Feature-facing account behavior is exposed through `core:accounts`, which maps Room rows into local account domain models and enforces source-compatible CRUD/default/delete rules. Currency catalog and local rate lookup behavior are exposed through `core:currency`.

## Identity Boundary

Android persistent storage must use local identifiers only:

- `local_profiles.id` is the local owner key for user data on the device.
- Domain tables reference `profile_id`, never server `user_id`.
- The schema must not add Telegram ID, Telegram username, Telegram first/last name, initData, bot/chat metadata, `legacy_*`, `source_*`, or source database IDs.
- Import code must map any source rows into fresh local IDs before writing to Room.
- First launch creates an offline `local_profiles` row through `LocalProfileRepository.ensureActiveProfile()` without auth or server identity.
- First launch also seeds the local default account and categories through `DefaultProfileSeedRepository`.
  The account currency is derived from the local profile/device language, unless a caller supplies an explicit currency code.
  Seed data never stores source identity fields or Telegram-derived profile metadata.
- The active profile selection is stored as the private DataStore preference `active_profile_id`; it is a device-local pointer, not a synced identity.

## Tables

| Table | Purpose |
| --- | --- |
| `local_profiles` | Local settings owner: language, display currencies, notification preferences, UI preferences, timestamps. |
| `accounts` | Local wallets/accounts with type, currency, default flag, include-in-total flag. |
| `categories` | Per-profile categories, including protected infrastructure categories for transfers and adjustments. System categories carry a stable `localization_key`; custom or user-renamed categories keep only their stored `name`. |
| `transactions` | Income/expense rows, account/category links, snapshot date, adjustment flag, local timestamp. |
| `transfers` | Account-to-account movements with linked local debit/credit transaction IDs. |
| `budgets` | Category budgets with notification state. |
| `recurring_transactions` | Scheduled transaction templates with account/category links and next-run timestamp. |
| `savings_goals` | Goal progress and optional linked account. |
| `goal_transactions` | Goal deposit/withdraw history. |
| `exchange_rate_snapshots` | Date-based currency conversion snapshots, stored as `rate_e8` fixed-point values. |
| `exchange_rate_overrides` | Profile-owned manual exchange rates that override snapshots from a date onward. |
| `transaction_templates` | Manual quick templates ordered per profile. |

## Indexes And Relationships

The v1 contract includes indexes for the expected offline reads:

- history and stats: `transactions(profile_id, created_at_epoch_millis)`, account/date, category/date, `snapshot_date`;
- account screens: `accounts(profile_id)`, `accounts(profile_id, name)`;
- default account changes must go through `AccountDao.setDefault()`, which marks and validates the new account before clearing other defaults in the same profile;
- categories: `categories(profile_id, name)`, profile/type, `categories(profile_id, localization_key)`, soft-delete filtering;
- budgets: unique `budgets(profile_id, category_id, period)`;
- recurring work: `recurring_transactions(profile_id, is_active, next_run_at_epoch_millis)` for profile reads and `recurring_transactions(is_active, next_run_at_epoch_millis)` for global due work;
- savings: goal/profile and goal transaction history indexes;
- transfers: profile/date plus from/to account and linked transaction indexes;
- exchange rates: unique snapshot `(snapshot_date, base_currency, target_currency)` plus `(base_currency, target_currency, snapshot_date)` for latest-at-or-before lookup;
- manual exchange rates: unique override `(profile_id, effective_date, base_currency, target_currency)` plus `(profile_id, base_currency, target_currency, effective_date)` for profile-scoped latest-at-or-before lookup;
- templates: profile/order plus account/category indexes.

Foreign keys use local IDs. Profile deletion cascades profile-owned data. Account hard-deletes are restricted where deleting them would orphan financial records. Category removal is a soft delete so transaction, budget, recurring, and template history can keep valid local category IDs. Savings goals can unlink an account.

## Accounts Domain Layer

`RoomAccountsRepository` in `core:accounts` owns the local account data contract above Room:

- first created account in a profile becomes default; later accounts are non-default until explicitly promoted;
- account updates can change name, icon, color, type, and include-in-total, but currency is immutable after creation;
- `setDefaultAccount()` preserves exactly one default account per profile through the transactional DAO helper;
- deleting the only account is rejected;
- deleting a default account with exactly two accounts auto-promotes the remaining account; deleting a default account with more than two accounts requires selecting a new default first;
- accounts referenced by transactions, transfers, recurring transactions, or transaction templates are rejected before SQLite foreign-key failure;
- account balances are derived from local `transactions` rows (`income` positive, `expense` negative) and are not stored in `accounts`.

## Categories Domain Layer

`RoomCategoriesRepository` in `core:categories` owns the local category data contract above Room:

- editable category lists are profile-scoped, hide soft-deleted rows, and exclude protected infrastructure categories;
- system category rows use `localization_key` to resolve the display name for the current profile language at read time;
- user-created categories and system categories renamed by the user keep their stored `name` and have no localization key;
- type filters include `both` for `expense` and `income`, while `savings` remains its own category type;
- frequency sorting counts non-adjustment transactions in the same profile and uses name ascending as the tie-breaker;
- category creation and updates normalize text fields, reject empty names, and reject runtime creation or conversion to `transfer`/`adjustment`;
- protected infrastructure categories (`transfer`, `adjustment`) can be looked up for internal flows but cannot be updated or deleted;
- `deleteCategory()` writes `deleted_at_epoch_millis` instead of hard-deleting rows, preserving existing financial history references.

## Currency Rates Domain Layer

`core:currency` owns the local currency catalog and rate behavior above Room:

- `IsoCurrencyCatalog` exposes offline ISO 4217 currencies from the platform runtime only when a built-in system quote exists, supports code/name search, and excludes non-currency test/no-currency pseudo codes plus runtime legacy/special codes without a system rate;
- `RoomCurrencyRatesRepository` stores seed and daily snapshot rates in `exchange_rate_snapshots`;
- rates use `rate_e8` fixed-point values, where `100_000_000` means `1.0`;
- same-currency conversion returns `1.0` without requiring a database row;
- historical lookup uses the exact snapshot date or the latest snapshot before it;
- manual overrides are stored per local profile and take priority over snapshots for the same currency pair on or after the override effective date;
- if no manual override or saved snapshot exists, `core:currency` resolves a built-in system rate for supported ISO pairs so cross-currency flows can run before the first online update;
- online updates select target currencies from active profile data, including accounts, transactions, transfers, budgets, recurring transactions, savings goals, transaction templates, display currencies, and manual override pairs;
- overrides do not affect other local profiles and are removed when their profile is deleted.

## Default Seed

`DefaultProfileSeedRepository` ports the current Mini App first-launch seed behavior into the offline Android database:

- creates one default checking account when the profile has no accounts;
- localizes the default account name and derives currency from the normalized profile language (`en` -> `USD`, `ru` -> `RUB`, `uk` -> `UAH`, and the rest of the source-supported language table);
- seeds the 9 user-editable source categories with stable system localization keys only when the profile has no editable categories;
- always ensures protected per-profile infrastructure categories for `transfer` and `adjustment` with stable system localization keys;
- uses idempotent inserts, so repeated app startup does not duplicate seed rows.

## Migrations

Room version `1` is the initial schema. Room version `2` adds the recurring run marker table.
Room version `3` adds local notification and UI preference fields.
Room version `4` adds `categories.localization_key` and `index_categories_profile_id_localization_key`.
`MoneyTrackerDatabaseMigrations.ALL` contains every consecutive migration from version `1`.

When the schema changes:

1. Add a Room `Migration`.
2. Export the new Room schema JSON under `core/database/schemas`.
3. Update this document with the new table/index/identity behavior.
4. Keep `MoneyTrackerDatabaseSchemaTest` guarding against source identity fields.

## Version 2 Addendum

Room version `2` adds `recurring_transaction_runs`, a local marker table used by recurring due processing.
The table records one processed marker per `(profile_id, recurring_transaction_id, scheduled_for_epoch_millis)`
and links to the generated local transaction when one is created.

This marker is not a user-authored financial template and is not part of the backup contract or UI contract.
It exists to keep app-open catch-up and periodic WorkManager processing idempotent if the same due run is observed more than once.

## Version 4 Addendum

Room version `4` adds `categories.localization_key` for system category display names. The migration maps existing rows only when the stored name, category type, icon, color, and protected flag match a known default/system category translation. This keeps custom categories and user-renamed system categories as stored names while allowing mapped system categories to display in the current profile language after language changes.
