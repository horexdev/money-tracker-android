# MT-G06 Migration Rehearsal Report

Дата прогона: 2026-07-03

## Scope

Репетиция проведена для `MoneyTrackerBackup` JSON v1 на anonymized fixture
`core/backup/src/test/resources/fixtures/money_tracker_backup_v1_anonymized_full.json`.
Фактический Postgres/staging export не запускался, потому что в локальном окружении
`DATABASE_URL` отсутствует.

Карточка: GitHub Issue #61 `[MT-G06] Провести migration rehearsal`.

## Source Context

Проверены файлы source repo `D:\Projects\money-tracker`:

- `README.md`
- `Makefile`
- `CLAUDE.md`
- `mempalace.yaml`
- `db/migrations/*.sql`
- `docs/android-backup-exporter.md`
- `cmd/source-exporter/main.go`
- `internal/backup/format.go`
- `internal/backup/source.go`
- `internal/backup/postgres_reader.go`
- `internal/backup/validator.go`
- `internal/backup/postgres_exporter_integration_test.go`

Текущая source-семантика: `cmd/source-exporter` пишет plain `MoneyTrackerBackup`
JSON v1, не сериализует Telegram/source identity поля и source DB IDs, а связи
перестраивает через export-local refs вида `profile:p000001`, `account:a000001`,
`transaction:t000001`.

Команда для реального staging rehearsal, когда доступен staging-compatible DSN:

```powershell
go run ./cmd/source-exporter --database-url "$env:DATABASE_URL" --output <private-temp>\moneytracker.mtbackup.json
```

Полученный файл должен оставаться вне git, потому что это финансовые данные.

## Android Pipeline

Проверенный локальный путь:

1. Decode fixture через `MoneyTrackerBackupV1Json.decodeFromString`.
2. Restore dry-run через `MoneyTrackerBackupV1Validator.validateJson`.
3. Import dry-run через `MoneyTrackerBackupImporter` и fake import store, который
   сохраняет только локальные IDs.
4. Rehearsal checker `tools/migration_rehearsal.py` сравнивает financial totals с
   `docs/migration-rehearsal-mt-g06-expected-totals.json` и сканирует identity markers.

UI route импорта/экспорта не менялся. Текущий UI слой использует тот же preview
validation result и не показывает raw backup refs или forbidden identity paths.

## Rehearsal Command

```powershell
python .\tools\migration_rehearsal.py `
  --backup .\core\backup\src\test\resources\fixtures\money_tracker_backup_v1_anonymized_full.json `
  --expected .\docs\migration-rehearsal-mt-g06-expected-totals.json `
  --forbidden-fragment 4242424242 `
  --forbidden-fragment 987654321 `
  --forbidden-fragment 81001 `
  --forbidden-fragment 82001 `
  --forbidden-fragment 83001 `
  --forbidden-fragment 84001 `
  --forbidden-fragment 85001 `
  --forbidden-fragment 86001 `
  --forbidden-fragment 87001 `
  --forbidden-fragment 88001 `
  --forbidden-fragment 89001 `
  --markdown
```

Результат: passed.

## Financial Totals

| Currency | Income | Expense | Net | Included account total |
| --- | ---: | ---: | ---: | ---: |
| EUR | 46000 | 0 | 46000 | 46000 |
| RUB | 10000 | 0 | 10000 | 10000 |
| USD | 250000 | 52750 | 197250 | 197250 |

Дополнительные сверки:

- Transfers: `USD->EUR`, 1 transfer, source amount `50000`, destination linked transaction `46000`.
- Budgets: `USD 100000`.
- Recurring transactions: `USD -121500` net.
- Savings current: `EUR 46000`, `USD 15000`.
- Goal transactions: `EUR +46000`, `USD -5000`.
- Exchange rate snapshots: `2`.

## Identifier Scan

Статус: passed.

Проверено:

- нет keys `id`, `*_id`, `telegram*`, `username`, `first_name`, `last_name`,
  `init_data*`, `bot*`, `chat*`, `legacy*`, `source*`;
- refs не содержат `telegram`, `source`, `legacy`, `init_data`, `bot`, `chat`;
- fixture не содержит raw fragments из source exporter integration fixture:
  `4242424242`, `987654321`, `81001`-`89001`, `account:501`,
  `transaction:701`, `profile:901`, `from_tx_id`, `to_tx_id`;
- нет SQLCipher/passphrase/keystore/device-bound/secret/password markers.

## Local Validation

Выполненные команды:

```powershell
python .\tools\migration_rehearsal.py --backup .\core\backup\src\test\resources\fixtures\money_tracker_backup_v1_anonymized_full.json --expected .\docs\migration-rehearsal-mt-g06-expected-totals.json --forbidden-fragment 4242424242 --forbidden-fragment 987654321 --forbidden-fragment 81001 --forbidden-fragment 82001 --forbidden-fragment 83001 --forbidden-fragment 84001 --forbidden-fragment 85001 --forbidden-fragment 86001 --forbidden-fragment 87001 --forbidden-fragment 88001 --forbidden-fragment 89001 --markdown
.\gradlew.bat :core:backup:test
git diff --check
```

Результаты:

- `tools/migration_rehearsal.py`: passed, totals comparison passed,
  identifier scan passed.
- `.\gradlew.bat :core:backup:test`: `BUILD SUCCESSFUL`.
- `git diff --check`: passed.

## Limitations

- Фактический source Postgres export не запускался: `DATABASE_URL` отсутствует в
  локальном окружении.
- Файл из реального staging export не должен коммититься. Для повторения нужно
  сгенерировать его во временную private-директорию, прогнать checker и удалить
  файл после restore rehearsal.
- Full Android validation не требуется для этой задачи, потому что production code,
  Gradle config, Room schema, backup DTO/API contract, UI/navigation и encryption
  semantics не менялись.
