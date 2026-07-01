# Room Schema V1

## Scope

`core:database` owns the first local Room contract for the Android offline migration.

The schema is based on the current Mini App PostgreSQL migrations, SQL queries, backend services, and web API types. It intentionally adapts server `users` into local Android profiles and does not copy Telegram identity fields or source database identifiers.

Production opens this schema through SQLCipher. The encrypted database open flow and Android Keystore-backed passphrase storage are documented in `docs/database-encryption.md`.

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
| `categories` | Per-profile categories, including protected infrastructure categories for transfers and adjustments. |
| `transactions` | Income/expense rows, account/category links, snapshot date, adjustment flag, local timestamp. |
| `transfers` | Account-to-account movements with linked local debit/credit transaction IDs. |
| `budgets` | Category budgets with notification state. |
| `recurring_transactions` | Scheduled transaction templates with account/category links and next-run timestamp. |
| `savings_goals` | Goal progress and optional linked account. |
| `goal_transactions` | Goal deposit/withdraw history. |
| `exchange_rate_snapshots` | Date-based currency conversion snapshots, stored as `rate_e8` fixed-point values. |
| `transaction_templates` | Manual quick templates ordered per profile. |

## Indexes And Relationships

The v1 contract includes indexes for the expected offline reads:

- history and stats: `transactions(profile_id, created_at_epoch_millis)`, account/date, category/date, `snapshot_date`;
- account screens: `accounts(profile_id)`, `accounts(profile_id, name)`;
- default account changes must go through `AccountDao.setDefault()`, which marks and validates the new account before clearing other defaults in the same profile;
- categories: `categories(profile_id, name)`, profile/type, soft-delete filtering;
- budgets: unique `budgets(profile_id, category_id, period)`;
- recurring work: `recurring_transactions(profile_id, is_active, next_run_at_epoch_millis)` for profile reads and `recurring_transactions(is_active, next_run_at_epoch_millis)` for global due work;
- savings: goal/profile and goal transaction history indexes;
- transfers: profile/date plus from/to account and linked transaction indexes;
- exchange rates: unique `(snapshot_date, base_currency, target_currency)` plus `(base_currency, target_currency, snapshot_date)` for latest-at-or-before lookup;
- templates: profile/order plus account/category indexes.

Foreign keys use local IDs. Profile deletion cascades profile-owned data. Account/category deletes are restricted where deleting them would orphan financial records, except savings goals can unlink an account.

## Default Seed

`DefaultProfileSeedRepository` ports the current Mini App first-launch seed behavior into the offline Android database:

- creates one default checking account when the profile has no accounts;
- localizes the default account name and derives currency from the normalized profile language (`en` -> `USD`, `ru` -> `RUB`, `uk` -> `UAH`, and the rest of the source-supported language table);
- seeds the 9 user-editable source categories only when the profile has no editable categories;
- always ensures protected per-profile infrastructure categories for `transfer` and `adjustment`;
- uses idempotent inserts, so repeated app startup does not duplicate seed rows.

## Migrations

Room version `1` is the initial schema. `MoneyTrackerDatabaseMigrations.ALL` is empty until version `2`.

When the schema changes:

1. Add a Room `Migration`.
2. Export the new Room schema JSON under `core/database/schemas`.
3. Update this document with the new table/index/identity behavior.
4. Keep `MoneyTrackerDatabaseSchemaTest` guarding against source identity fields.
