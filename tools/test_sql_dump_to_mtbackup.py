from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
sys.path.insert(0, str(SCRIPT_DIR))

import sql_dump_to_mtbackup as converter  # noqa: E402


class SqlDumpToMtBackupTest(unittest.TestCase):
    def test_synthetic_dump_matches_expected_backup(self) -> None:
        tables = converter.load_copy_tables(SCRIPT_DIR / "fixtures" / "sql_dump_converter_synthetic.sql")
        user = converter.select_user(
            tables,
            {
                "user-id": "1001",
                "username": "demo_user",
                "first-name": "Demo",
                "last-name": "Person",
            },
        )

        backup = converter.build_backup(tables, user, created_at_epoch_millis=1_783_065_600_000)
        actual = json.loads(converter.encode_backup(backup))
        expected = json.loads(
            (
                REPO_ROOT
                / "core"
                / "backup"
                / "src"
                / "test"
                / "resources"
                / "fixtures"
                / "sql_dump_converter_synthetic_backup_v1.json"
            ).read_text(encoding="utf-8"),
        )

        self.assertEqual(expected, actual)

    def test_cli_rejects_identity_fragment_leak(self) -> None:
        fixture = SCRIPT_DIR / "fixtures" / "sql_dump_converter_synthetic.sql"
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir) / "backup.json"
            exit_code = converter.main(
                [
                    "--dump",
                    str(fixture),
                    "--output",
                    str(output),
                    "--user-id",
                    "1001",
                    "--username",
                    "demo_user",
                    "--first-name",
                    "Demo",
                    "--last-name",
                    "Person",
                    "--forbidden-fragment",
                    "Lunch",
                ],
            )

        self.assertEqual(1, exit_code)
        self.assertFalse(output.exists())

    def test_missing_relationship_fails(self) -> None:
        tables = converter.load_copy_tables(SCRIPT_DIR / "fixtures" / "sql_dump_converter_synthetic.sql")
        user = converter.select_user(tables, {"user-id": "1001"})
        transactions = tables["transactions"].rows
        transactions[0]["category_id"] = "999999"

        with self.assertRaisesRegex(converter.ConversionError, "transaction category"):
            converter.build_backup(tables, user, created_at_epoch_millis=1)


if __name__ == "__main__":
    unittest.main()
