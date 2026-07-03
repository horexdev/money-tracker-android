# Support And Known Limitations

Last reviewed: 2026-07-03.

This document is the support-facing source for the current Money Tracker Android
offline release. It should stay aligned with `docs/money-tracker-backup-v1.md`,
`docs/migration-rehearsal-mt-g06.md`, `docs/play-release-checklist.md`, and
`docs/release-builds.md`.

## Current User-Visible Limitations

- Money Tracker is offline-first. The current Android app does not declare
  `INTERNET`, does not connect to a Money Tracker server, and does not sync
  records between devices.
- Profiles are local to the Android installation. They are not server accounts,
  Telegram accounts, or shared household accounts.
- There is no server-side account deletion or recovery flow because the app
  does not create server accounts and does not upload local finance records.
- If the app is uninstalled or Android app storage is cleared, local records are
  removed from the device. Recovery is possible only from a user-created backup
  file.
- App-private Android cloud backup is disabled with
  `android:allowBackup="false"`. A new device will not automatically receive the
  app-private database from Google backup.
- The Room database is encrypted with SQLCipher and a device-bound Android
  Keystore key. Copying private database files to another device is not a
  supported migration path.
- Portable export/import uses the MoneyTrackerBackup v1 document flow. Backup
  files are controlled by the user through the Android document picker and are
  not uploaded by the app.
- Backup import restores selected backup profiles as new local profiles with
  editable labels. It does not merge duplicate transactions, reconcile two
  devices, or resolve conflicts with an existing profile.
- Global exchange-rate snapshots may be upserted during import. Profile-owned
  records are inserted under newly created local profile IDs.
- Telegram login, Telegram sessions, Telegram profile fields, bot/chat metadata,
  source database IDs, `legacy_*` fields, and source-side identifiers are not
  imported or stored in Android backups.
- Bank connections, payments, investment advice, credit scoring, ads,
  analytics, crash reporting, remote push notifications, and server backup are
  not part of the current Android app.
- Notifications are local reminders. Delivery can still depend on Android
  notification permission, device settings, and operating system scheduling.

## Backup And Import Caveats

Plain backup files contain financial data in readable JSON. Users should store
plain exports only in locations they trust. Password-protected exports wrap the
same MoneyTrackerBackup v1 payload in a portable encrypted container and are the
preferred format for sharing or cloud storage.

Backup encryption is separate from database encryption:

- the SQLCipher database passphrase is never exported;
- Android Keystore aliases and device-bound keys are never exported;
- backup passwords are never serialized in backup files;
- support cannot recover a forgotten backup password;
- a wrong password or tampered encrypted payload blocks restore before any
  database write starts.

Import always runs validation before restoring. A backup is blocked when it has
unsupported format or version, invalid JSON shape, unresolved refs, duplicate
refs, forbidden ref parts, or forbidden external identity fields. Restore writes
run inside one Room transaction, so a failed import rolls back profile rows and
exchange-rate snapshots written by that attempt.

The current UI previews the backup before restore, lets the user select profiles,
and lets the user edit the target local profile labels. It does not expose raw
backup refs or forbidden identity paths to the user.

## Migration Support Boundary

The MT-G06 rehearsal covered the Android backup pipeline with the anonymized
full-domain fixture and identifier scan documented in
`docs/migration-rehearsal-mt-g06.md`. A real staging export is a private
operational artifact and must stay outside git.

Support and migration follow-up should ask for rehearsal command results,
validation titles, financial total mismatches, or identifier scan status before
asking for any artifact. Do not request source database dumps, real staging
exports, Telegram/source identity values, or source database IDs in ordinary
support channels.

## Safe Support And Debug Information

Support requests may ask for the following information:

- app version name and version code, if visible in the installed build;
- release channel or install source, such as internal test, Play track, or local
  debug build;
- Android version and device model;
- app language and device region;
- the screen or action that failed, such as export, preview, restore, password
  retry, notification permission, or release install;
- the visible error text or validation title;
- whether the backup is plain `MoneyTrackerBackup` v1 or an encrypted
  `MoneyTrackerBackupEncryptedContainer` v1;
- whether the backup preview shows profile counts and warnings before restore;
- the storage provider category used for the file, such as local Files app or a
  cloud document provider;
- approximate time of the failed action and whether the device was offline.

Support requests must not ask users to send:

- backup passwords or password hints;
- SQLCipher passphrases, Android Keystore material, signing keys, or keystore
  files;
- raw app-private database files;
- unredacted backup JSON unless a secure, explicit escalation path is approved;
- screenshots with personal finance records, real names, account numbers,
  emails, phone numbers, or file paths that identify the user;
- Telegram identifiers, Telegram usernames, first or last names from Telegram,
  init data, bot/chat metadata, source database IDs, or `legacy_*` identifiers.

If a screenshot is needed, ask the user to use sample data or redact names,
amounts, notes, account numbers, and file paths. For backup diagnostics, ask for
the visible validation title, backup metadata, a redacted screenshot, or a local
reproduction path. A controlled escalation must not require the backup password
or ask the user to send it in any channel.

## Backup And Import FAQ

### Does Money Tracker sync data to a server?

No. The current Android app is local-only and has no server sync. Data stays in
the encrypted on-device database unless the user exports a backup file.

### Can support restore data after uninstall or storage clear?

Only if the user has a backup file. The app does not keep a server copy, and
app-private Android backup is disabled.

### Can I move data to another Android device?

Yes, by exporting a MoneyTrackerBackup file from the old device and importing it
on the new device. Copying the private SQLCipher database file is not supported
because the database key is device-bound.

### Should I use an encrypted backup?

Use encrypted backups for any file that may leave the device, be stored in a
cloud provider, or be sent through another app. Plain JSON backups are useful for
local inspection and tests but contain readable financial data.

### Can a forgotten backup password be recovered?

No. The backup password is not stored by the app. Without the password, an
encrypted backup cannot be restored.

### Does backup include Telegram or source Mini App identifiers?

No. Android backup v1 intentionally excludes Telegram IDs, usernames, first and
last names from Telegram, init data, bot/chat metadata, source database IDs,
`legacy_*` fields, and source-side identifiers. Import validation blocks backup
documents that contain those fields.

### What happens if import fails?

Validation failures happen before database writes. Runtime restore failures are
inside one Room transaction, so rows written by that failed attempt are rolled
back.

### Does import replace my current data?

No. The current flow restores selected backup profiles as new local profiles.
It does not merge or delete an existing local profile. Global exchange-rate
snapshots may be upserted by date/currency key.

### Can I import only one profile from a backup?

Yes. The restore preview lets the user select profiles and set local profile
labels before restore.

### Can I import a newer backup version?

Not unless the app supports that backup version. Unsupported format or version
blocks restore.

### Can support diagnose a backup without seeing my financial data?

Usually yes. Start with app version, Android version, visible validation title,
backup format/version, encrypted/plain status, profile counts shown in preview,
and the action that failed. Do not send raw backup contents unless a secure
escalation path is agreed first.

## Maintainer Checklist For Support Changes

- Recheck the release manifest before claiming offline-only behavior.
- Keep privacy, Data safety, and support text aligned when analytics, crash
  reporting, server sync, file upload, support SDKs, or telemetry are added.
- Keep backup FAQ aligned with `MoneyTrackerBackup` v1 validation and import
  semantics.
- Do not add real user data, Telegram identifiers, source database IDs, backup
  passwords, SQLCipher keys, Android Keystore material, or signing secrets to
  docs, fixtures, screenshots, logs, PRs, or issues.
- For docs-only support updates, `git diff --check` is the required local
  validation.
