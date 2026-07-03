# SQL Dump to MoneyTrackerBackup Converter

`tools/sql_dump_to_mtbackup.py` converts a plain or gzip-compressed PostgreSQL
SQL dump that contains `COPY ... FROM stdin` blocks into `MoneyTrackerBackup`
JSON v1.

The selector fields (`--user-id`, `--username`, `--first-name`, `--last-name`)
are used only to choose the source `users` row. They are never written to the
backup. The converter fails before writing output if any provided selector value
would appear in the generated backup JSON.

## Private Artifact Run

Generate real user artifacts only in an ignored local path, for example:

```powershell
python .\tools\sql_dump_to_mtbackup.py `
  --dump <private dump .sql.gz> `
  --output .\.private\issue-134\moneytracker.mtbackup.json `
  --totals-output .\.private\issue-134\moneytracker-totals.json `
  --user-id <source users.id> `
  --username <source username> `
  --first-name <source first_name> `
  --last-name <source last_name>
```

Keep the `.private/` directory out of git. The backup contains restore data and
must not be committed.

## Rehearsal Checks

Run deterministic converter tests over synthetic data:

```powershell
python -m unittest tools.test_sql_dump_to_mtbackup
```

Run the generic backup rehearsal checker:

```powershell
python .\tools\migration_rehearsal.py `
  --backup .\.private\issue-134\moneytracker.mtbackup.json `
  --expected .\.private\issue-134\moneytracker-totals.json `
  --forbidden-fragment <source users.id> `
  --forbidden-fragment <source username> `
  --forbidden-fragment <source first_name> `
  --forbidden-fragment <source last_name> `
  --markdown
```

Run the Android validator/import rehearsal against the private artifact:

```powershell
.\gradlew.bat :core:backup:testDebugUnitTest `
  --tests dev.horex.moneytracker.core.backup.MoneyTrackerSqlDumpConverterRehearsalTest.privateBackupArtifactValidatesAndImportsWhenConfigured `
  -DmoneyTrackerBackupFile=<absolute private backup path>
```

The default `.\gradlew.bat test` run keeps the private-artifact rehearsal skipped
unless `moneyTrackerBackupFile` is provided.
