# Mini App Shutdown Checklist

Last reviewed: 2026-07-03.

This checklist prepares the later retirement of the old Money Tracker Mini App
after the Android offline release. It is a planning document only: do not stop,
disable, delete, or reconfigure the old production service as part of MT-G08.

## Source Context

Reviewed source Mini App files:

- `D:\Projects\money-tracker\CLAUDE.md`
- `D:\Projects\money-tracker\README.md`
- `D:\Projects\money-tracker\Makefile`
- `D:\Projects\money-tracker\mempalace.yaml`
- `D:\Projects\money-tracker\docs\android-backup-exporter.md`
- `D:\Projects\money-tracker\cmd\source-exporter\main.go`
- `D:\Projects\money-tracker\internal\backup\format.go`
- `D:\Projects\money-tracker\internal\backup\source.go`
- `D:\Projects\money-tracker\internal\backup\postgres_reader.go`
- `D:\Projects\money-tracker\internal\backup\validator.go`
- `D:\Projects\money-tracker\internal\api\export.go`
- `D:\Projects\money-tracker\internal\service\export.go`
- `D:\Projects\money-tracker\web\src\app\App.tsx`
- `D:\Projects\money-tracker\web\src\features\export\index.tsx`
- `D:\Projects\money-tracker\web\src\shared\api\export.ts`
- relevant source migrations for users, accounts, transactions, transfers,
  budgets, recurring transactions, savings goals, goal transactions, exchange
  rate snapshots, notification preferences, UI preferences, and templates.

The source-side Android backup exporter is `cmd/source-exporter`. It writes
`MoneyTrackerBackup` JSON v1 for the Android restore pipeline. The old Mini App
also has a user CSV export route, but that route covers transaction history only
and is not the full Android migration path.

Related Android docs:

- [MoneyTrackerBackup V1](money-tracker-backup-v1.md)
- [MT-G06 Migration Rehearsal Report](migration-rehearsal-mt-g06.md)
- [Play Release Checklist](play-release-checklist.md)
- [Support And Known Limitations](support-and-known-limitations.md)

## Shutdown Scope

In scope for the later shutdown run:

- keep the old Mini App available long enough for Android migration requests;
- keep the source-side backup exporter buildable and documented during that
  window;
- give users a clear migration path before the old service is retired;
- capture final operational evidence before disabling old components.

Out of scope for this checklist:

- changing Android code, Gradle, Room schema, navigation, settings UI, budgets,
  or notification behavior;
- changing the source Mini App code or production infrastructure;
- closing the old Mini App immediately;
- adding new sync or server-account behavior to Android.

## Data Boundary

Migration is limited to portable finance data needed by Android:

- local profile preferences;
- accounts;
- categories;
- transactions, transfers, and adjustments;
- budgets and notification preference values;
- recurring transactions;
- savings goals and goal transactions;
- exchange rate snapshots and overrides;
- transaction templates.

Do not migrate old platform runtime metadata, old service row keys, admin-only
operator data, bot context, app-private secrets, database credentials, signing
material, SQLCipher material, Android Keystore material, or backup passwords.
The exporter must continue to generate export-local relationship refs that are
valid only inside one backup file.

## Exporter Availability Window

Use `T0` for the Android production release date.

| Window | Old Mini App state | Exporter state | Exit criteria |
| --- | --- | --- | --- |
| `T0 - 14` through `T0 - 1` | Production remains online. Only security or migration-critical fixes are allowed. | Confirm `cmd/source-exporter` builds from the source repo `develop` branch. | MT-G06 rehearsal is accepted, Android import path is release-ready, support copy is prepared. |
| `T0` through `T0 + 30` | Keep production online for user verification and migration requests. If a read-only mode is available later, prefer it after the announcement; otherwise avoid feature changes. | Keep operator exports available for approved migration requests. | Users have had at least 30 calendar days to request and verify migration. |
| `T0 + 31` through `T0 + 44` | Stop accepting routine migration requests, but keep a rollback path and final snapshot available. | Keep exporter available only for approved late escalations. | No active restore blockers, no unresolved high-priority migration cases, final reminder sent. |
| After `T0 + 44` | Execute the separate shutdown task only after owner sign-off. | Archive build and run instructions with the final operational record. | Final snapshot captured, rollback decision made, shutdown task approved. |

Extend the window if Android restore validation has a blocking defect, Play
rollout is paused, support volume shows unresolved migration failures, or the
operator exporter cannot produce a valid backup from the current production
data.

## Maintainer Shutdown Plan

### Before Android Release

- [ ] Confirm dependency #61 is done and its rehearsal report remains current.
- [ ] Re-run or review the source exporter command from the source repo when a
  staging-compatible database URL is available:

```powershell
go run ./cmd/source-exporter --database-url "$env:DATABASE_URL" --output <private-temp>\moneytracker.mtbackup.json
```

- [ ] Keep generated backup files out of git, issue comments, PRs, and normal
  support channels.
- [ ] Verify Android release assumptions in `docs/play-release-checklist.md`.
- [ ] Verify backup/import support text in
  `docs/support-and-known-limitations.md`.
- [ ] Prepare announcement copy with the `T0`, `T0 + 30`, and `T0 + 44` dates
  filled in.
- [ ] Assign an owner for user migration requests and late escalations.

### During The Migration Window

- [ ] Keep the old Mini App, API, database, and source exporter reachable for
  approved migration work.
- [ ] Track each user migration request outside the public repo.
- [ ] Generate backup files only in a private temporary directory.
- [ ] Prefer encrypted backup delivery when the file leaves the operator
  machine.
- [ ] Ask users to confirm Android restore by checking account totals, budgets,
  recurring entries, savings goals, templates, and recent transactions.
- [ ] Remove temporary generated files after each completed migration.
- [ ] Send a reminder before the `T0 + 30` request deadline.

### Final Shutdown Readiness

- [ ] No active Android restore blocker remains open.
- [ ] No unresolved support escalation still needs the old service online.
- [ ] Final production database snapshot has been captured and its retention
  owner is documented.
- [ ] Source exporter instructions and final command output summary are stored
  in the private operations record.
- [ ] Secrets and service credentials have an approved removal or retention
  decision.
- [ ] The separate shutdown task names the exact services to stop, the rollback
  window, and the owner who can restore access if needed.

## User Migration Instructions

Use this copy when announcing the Android migration window. Fill in the exact
calendar dates before sending it.

```text
Money Tracker is moving from the old Mini App to the Android offline app.

The old Mini App will stay available for migration requests until <T0 + 30>.
Please migrate before that date. A late support window remains open until
<T0 + 44>, but routine requests should be completed before the main deadline.

To migrate:

1. Install the Money Tracker Android release.
2. Request a MoneyTrackerBackup migration file from support during the migration
   window.
3. Store the received file in a private location on your device.
4. In Android, open Import / export, choose the backup file, preview it, select
   the profiles you want to restore, and start restore.
5. After restore, check accounts, recent transactions, transfers, budgets,
   recurring entries, savings goals, templates, and display settings.
6. Keep the old Mini App available until you confirm the Android data looks
   correct.
7. After verification, keep the backup file only if you intentionally want a
   personal copy. Delete temporary copies you do not need.

The Android app is offline-first. It stores restored finance records locally on
your device and does not require the old Mini App to keep working after your
migration is verified.
```

If a user reports a mismatch, ask for the Android app version, the visible
restore validation title, the affected finance area, and a redacted screenshot
when needed. Do not ask for backup passwords, raw database files, unredacted
financial records, or old platform runtime metadata through ordinary support
channels.

## Final Shutdown Task Template

Create a separate implementation task for the actual shutdown. It should include
at least:

- target shutdown date and rollback deadline;
- final migration-request count and unresolved escalation count;
- final snapshot location and retention owner;
- exact systemd services, deploy units, domains, scheduled jobs, and secrets to
  disable or retain;
- source exporter archive instructions;
- post-shutdown smoke checks for the Android support path and public links;
- communication owner for the final user notice.

Do not run this template from MT-G08. It is the input for a later approved task.
