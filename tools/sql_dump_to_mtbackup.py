#!/usr/bin/env python3
"""Convert a Money Tracker PostgreSQL SQL dump into MoneyTrackerBackup v1 JSON."""

from __future__ import annotations

import argparse
import gzip
import json
import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Any


MONEY_TRACKER_BACKUP_FORMAT = "MoneyTrackerBackup"
MONEY_TRACKER_BACKUP_VERSION = 1
RATE_SCALE_E8 = Decimal("100000000")

COPY_RE = re.compile(
    r'^COPY\s+(?:(?:"?public"?\.)?)"?([A-Za-z_][A-Za-z0-9_]*)"?\s+\((.*)\)\s+FROM\s+stdin;$',
)
TIMEZONE_WITHOUT_MINUTES_RE = re.compile(r"([+-]\d{2})$")


class ConversionError(RuntimeError):
    """Raised when the SQL dump cannot be converted safely."""


@dataclass(frozen=True)
class Table:
    columns: list[str]
    rows: list[dict[str, str | None]]


class RefGenerator:
    def __init__(self, profile_index: int) -> None:
        self.profile_index = profile_index
        self.account = 0
        self.category = 0
        self.transaction = 0
        self.transfer = 0
        self.budget = 0
        self.recurring = 0
        self.goal = 0
        self.goal_transaction = 0
        self.template = 0

    def next_profile(self) -> str:
        return f"profile:p{self.profile_index:06d}"

    def next_account(self) -> str:
        self.account += 1
        return f"account:a{self.account:06d}"

    def next_category(self) -> str:
        self.category += 1
        return f"category:c{self.category:06d}"

    def next_transaction(self) -> str:
        self.transaction += 1
        return f"transaction:t{self.transaction:06d}"

    def next_transfer(self) -> str:
        self.transfer += 1
        return f"transfer:tr{self.transfer:06d}"

    def next_budget(self) -> str:
        self.budget += 1
        return f"budget:b{self.budget:06d}"

    def next_recurring(self) -> str:
        self.recurring += 1
        return f"recurring:r{self.recurring:06d}"

    def next_goal(self) -> str:
        self.goal += 1
        return f"goal:g{self.goal:06d}"

    def next_goal_transaction(self) -> str:
        self.goal_transaction += 1
        return f"goal-transaction:gt{self.goal_transaction:06d}"

    def next_template(self) -> str:
        self.template += 1
        return f"template:tp{self.template:06d}"


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert a Money Tracker PostgreSQL SQL dump to a MoneyTrackerBackup v1 JSON file.",
    )
    parser.add_argument("--dump", required=True, type=Path, help="Input .sql or .sql.gz dump path.")
    parser.add_argument("--output", required=True, type=Path, help="Output .json/.mtbackup path, or - for stdout.")
    parser.add_argument("--user-id", type=int, help="Source users.id selector. Used only for selecting rows.")
    parser.add_argument("--username", help="Source username selector. Used only for selecting rows.")
    parser.add_argument("--first-name", help="Source first_name selector. Used only for selecting rows.")
    parser.add_argument("--last-name", help="Source last_name selector. Used only for selecting rows.")
    parser.add_argument(
        "--created-at-epoch-millis",
        type=int,
        help="Override top-level backup creation timestamp. Defaults to a deterministic max timestamp from included rows.",
    )
    parser.add_argument("--totals-output", type=Path, help="Optional private totals JSON output for rehearsal checks.")
    parser.add_argument(
        "--forbidden-fragment",
        action="append",
        default=[],
        help="Additional raw fragment that must not appear in the generated backup. Repeatable.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    identity = identity_from_args(args)
    try:
        tables = load_copy_tables(args.dump)
        user = select_user(tables, identity)
        backup = build_backup(
            tables=tables,
            user=user,
            created_at_epoch_millis=args.created_at_epoch_millis,
        )
        encoded = encode_backup(backup)
        scan_identity_fragments(encoded, identity, args.forbidden_fragment)
        write_output(args.output, encoded)
        if args.totals_output is not None:
            write_totals(args.totals_output, backup)
        print_summary(args.output, backup)
    except ConversionError as error:
        print(f"conversion failed: {error}", file=sys.stderr)
        return 1
    except OSError as error:
        print(f"I/O failed: {error}", file=sys.stderr)
        return 2
    return 0


def identity_from_args(args: argparse.Namespace) -> dict[str, str]:
    identity: dict[str, str] = {}
    if args.user_id is not None:
        if args.user_id <= 0:
            raise ConversionError("user id must be positive")
        identity["user-id"] = str(args.user_id)
    if args.username:
        identity["username"] = normalize_username(args.username)
    if args.first_name:
        identity["first-name"] = args.first_name
    if args.last_name:
        identity["last-name"] = args.last_name
    if not identity:
        raise ConversionError("at least one user identity selector is required")
    return identity


def load_copy_tables(path: Path) -> dict[str, Table]:
    tables: dict[str, Table] = {}
    with open_text_dump(path) as stream:
        table_name: str | None = None
        columns: list[str] = []
        rows: list[dict[str, str | None]] = []
        for line_number, raw_line in enumerate(stream, start=1):
            line = raw_line.rstrip("\n")
            if table_name is None:
                match = COPY_RE.match(line)
                if match is None:
                    continue
                table_name = match.group(1)
                columns = [column.strip().strip('"') for column in match.group(2).split(",")]
                rows = []
                continue

            if line == r"\.":
                tables[table_name] = Table(columns=columns, rows=rows)
                table_name = None
                columns = []
                rows = []
                continue

            values = parse_copy_text_row(line)
            if len(values) != len(columns):
                raise ConversionError(
                    f"COPY row column mismatch in {table_name} at dump line {line_number}",
                )
            rows.append(dict(zip(columns, values)))

    if table_name is not None:
        raise ConversionError(f"unterminated COPY block for table {table_name}")
    if "users" not in tables:
        raise ConversionError("dump does not contain a users COPY block")
    return tables


def open_text_dump(path: Path):
    if not path.is_file():
        raise ConversionError("dump file was not found")
    if path.suffix.lower() == ".gz":
        return gzip.open(path, "rt", encoding="utf-8", errors="strict", newline="")
    return path.open("rt", encoding="utf-8", errors="strict", newline="")


def parse_copy_text_row(line: str) -> list[str | None]:
    return [None if raw == r"\N" else unescape_copy_text(raw) for raw in line.split("\t")]


def unescape_copy_text(value: str) -> str:
    result: list[str] = []
    index = 0
    while index < len(value):
        char = value[index]
        if char != "\\":
            result.append(char)
            index += 1
            continue

        index += 1
        if index >= len(value):
            result.append("\\")
            break

        escaped = value[index]
        mapping = {
            "b": "\b",
            "f": "\f",
            "n": "\n",
            "r": "\r",
            "t": "\t",
            "v": "\v",
            "\\": "\\",
        }
        if escaped in mapping:
            result.append(mapping[escaped])
            index += 1
        elif escaped in "01234567":
            octal = escaped
            index += 1
            while index < len(value) and len(octal) < 3 and value[index] in "01234567":
                octal += value[index]
                index += 1
            result.append(chr(int(octal, 8)))
        else:
            result.append(escaped)
            index += 1
    return "".join(result)


def select_user(tables: dict[str, Table], identity: dict[str, str]) -> dict[str, str | None]:
    matches = []
    for user in table_rows(tables, "users"):
        if "user-id" in identity and str(required_int(user, "id")) != identity["user-id"]:
            continue
        if "username" in identity and normalize_username(optional_text(user, "username")) != identity["username"]:
            continue
        if "first-name" in identity and optional_text(user, "first_name") != identity["first-name"]:
            continue
        if "last-name" in identity and optional_text(user, "last_name") != identity["last-name"]:
            continue
        matches.append(user)

    if not matches:
        raise ConversionError("no source user matched the provided identity selectors")
    if len(matches) > 1:
        raise ConversionError("more than one source user matched the provided identity selectors")
    return matches[0]


def build_backup(
    tables: dict[str, Table],
    user: dict[str, str | None],
    created_at_epoch_millis: int | None,
) -> dict[str, Any]:
    profile = build_profile(1, tables, user)
    backup = {
        "format": MONEY_TRACKER_BACKUP_FORMAT,
        "version": MONEY_TRACKER_BACKUP_VERSION,
        "created_at_epoch_millis": 0,
        "profiles": [profile],
        "exchange_rate_snapshots": build_exchange_rate_snapshots(tables),
    }
    backup["created_at_epoch_millis"] = (
        created_at_epoch_millis
        if created_at_epoch_millis is not None
        else max_epoch_millis(backup)
    )
    return backup


def build_profile(
    profile_index: int,
    tables: dict[str, Table],
    user: dict[str, str | None],
) -> dict[str, Any]:
    user_id = required_int(user, "id")
    refs = RefGenerator(profile_index)
    account_refs_by_id: dict[int, str] = {}
    category_refs_by_id: dict[int, str] = {}
    transaction_refs_by_id: dict[int, str] = {}
    goal_refs_by_id: dict[int, str] = {}

    accounts = sorted(
        rows_for_user(tables, "accounts", user_id),
        key=lambda row: (not bool_value(row, "is_default"), timestamp_sort_key(row, "created_at"), required_int(row, "id")),
    )
    transactions = sorted(
        rows_for_user(tables, "transactions", user_id),
        key=lambda row: (timestamp_sort_key(row, "created_at"), required_int(row, "id")),
    )
    transfers = sorted(
        rows_for_user(tables, "transfers", user_id),
        key=lambda row: (timestamp_sort_key(row, "created_at"), required_int(row, "id")),
    )
    budgets = sorted(
        rows_for_user(tables, "budgets", user_id),
        key=lambda row: (timestamp_sort_key(row, "created_at"), required_int(row, "id")),
    )
    recurring = sorted(
        rows_for_user(tables, "recurring_transactions", user_id),
        key=lambda row: (timestamp_sort_key(row, "created_at"), required_int(row, "id")),
    )
    savings_goals = sorted(
        rows_for_user(tables, "savings_goals", user_id),
        key=lambda row: (timestamp_sort_key(row, "created_at"), required_int(row, "id")),
    )
    goal_transactions = sorted(
        rows_for_user(tables, "goal_transactions", user_id),
        key=lambda row: (timestamp_sort_key(row, "created_at"), required_int(row, "id")),
    )
    templates = sorted(
        rows_for_user(tables, "transaction_templates", user_id),
        key=lambda row: (required_int(row, "sort_order"), timestamp_sort_key(row, "created_at"), required_int(row, "id")),
    )
    categories = included_categories(tables, user_id, transactions, budgets, recurring, templates)

    profile = {
        "ref": refs.next_profile(),
        "label": f"Profile {profile_index}",
        "language_code": normalized_default(optional_text(user, "language"), "en"),
        "display_currency_codes": parse_display_currencies(optional_text(user, "display_currencies")),
        "notification_preferences": {
            "notify_budget_alerts": bool_value(user, "notify_budget_alerts", default=True),
            "notify_recurring_reminders": bool_value(user, "notify_recurring_reminders", default=False),
            "notify_weekly_summary": bool_value(user, "notify_weekly_summary", default=False),
            "notify_goal_milestones": bool_value(user, "notify_goal_milestones", default=False),
        },
        "ui_preferences": {
            "stats_chart_style": require_one_of(
                "stats chart style",
                normalized_default(optional_text(user, "stats_chart_style"), "donut"),
                {"donut", "stacked_bar", "dual_bar", "profit_bars"},
            ),
            "animate_numbers": optional_bool(user, "animate_numbers"),
            "theme": require_one_of(
                "theme",
                normalized_default(optional_text(user, "theme"), "system"),
                {"system", "light", "dark"},
            ),
            "hide_amounts": bool_value(user, "hide_amounts", default=False),
        },
        "created_at_epoch_millis": epoch_millis(required_text(user, "created_at")),
        "updated_at_epoch_millis": epoch_millis(required_text(user, "updated_at")),
        "accounts": [],
        "categories": [],
        "transactions": [],
        "transfers": [],
        "budgets": [],
        "recurring_transactions": [],
        "savings_goals": [],
        "goal_transactions": [],
        "exchange_rate_overrides": [],
        "transaction_templates": [],
    }

    for source in accounts:
        ref = refs.next_account()
        account_refs_by_id[required_int(source, "id")] = ref
        profile["accounts"].append(
            {
                "ref": ref,
                "name": required_text(source, "name"),
                "icon": normalized_default(optional_text(source, "icon"), "wallet"),
                "color": normalized_default(optional_text(source, "color"), "#6366f1"),
                "type": require_one_of(
                    "account type",
                    required_text(source, "type"),
                    {"checking", "savings", "cash", "credit", "crypto"},
                ),
                "currency_code": normalize_currency(required_text(source, "currency_code")),
                "is_default": bool_value(source, "is_default"),
                "include_in_total": bool_value(source, "include_in_total"),
                "created_at_epoch_millis": epoch_millis(required_text(source, "created_at")),
                "updated_at_epoch_millis": epoch_millis(required_text(source, "updated_at")),
            },
        )

    for source in categories:
        ref = refs.next_category()
        category_refs_by_id[required_int(source, "id")] = ref
        profile["categories"].append(
            {
                "ref": ref,
                "name": required_text(source, "name"),
                "localization_key": None,
                "icon": normalized_default(optional_text(source, "icon"), "tag"),
                "type": require_one_of(
                    "category type",
                    required_text(source, "type"),
                    {"expense", "income", "both", "savings", "transfer", "adjustment"},
                ),
                "color": normalized_default(optional_text(source, "color"), "#6366f1"),
                "is_protected": bool_value(source, "is_protected", default=False),
                "updated_at_epoch_millis": epoch_millis(required_text(source, "updated_at")),
                "deleted_at_epoch_millis": optional_epoch_millis(source, "deleted_at"),
            },
        )

    for source in transactions:
        account_ref = lookup_ref(account_refs_by_id, required_int(source, "account_id"), "transaction account")
        category_ref = lookup_ref(category_refs_by_id, required_int(source, "category_id"), "transaction category")
        ref = refs.next_transaction()
        transaction_refs_by_id[required_int(source, "id")] = ref
        created_at = required_text(source, "created_at")
        profile["transactions"].append(
            {
                "ref": ref,
                "type": require_one_of(
                    "transaction type",
                    required_text(source, "type"),
                    {"expense", "income"},
                ),
                "amount_cents": required_int(source, "amount_cents"),
                "category_ref": category_ref,
                "account_ref": account_ref,
                "note": optional_text(source, "note"),
                "currency_code": normalize_currency(required_text(source, "currency_code")),
                "snapshot_date": date_string(optional_text(source, "snapshot_date"), fallback_timestamp=created_at),
                "created_at_epoch_millis": epoch_millis(created_at),
                "is_adjustment": bool_value(source, "is_adjustment", default=False),
            },
        )

    for source in transfers:
        from_tx_id = optional_int(source, "from_tx_id")
        to_tx_id = optional_int(source, "to_tx_id")
        profile["transfers"].append(
            {
                "ref": refs.next_transfer(),
                "from_account_ref": lookup_ref(
                    account_refs_by_id,
                    required_int(source, "from_account_id"),
                    "transfer from account",
                ),
                "to_account_ref": lookup_ref(
                    account_refs_by_id,
                    required_int(source, "to_account_id"),
                    "transfer to account",
                ),
                "amount_cents": required_int(source, "amount_cents"),
                "from_currency_code": normalize_currency(required_text(source, "from_currency_code")),
                "to_currency_code": normalize_currency(required_text(source, "to_currency_code")),
                "exchange_rate_e8": decimal_rate_to_e8(required_text(source, "exchange_rate")),
                "note": optional_text(source, "note"),
                "from_transaction_ref": optional_lookup_ref(transaction_refs_by_id, from_tx_id, "transfer from transaction"),
                "to_transaction_ref": optional_lookup_ref(transaction_refs_by_id, to_tx_id, "transfer to transaction"),
                "created_at_epoch_millis": epoch_millis(required_text(source, "created_at")),
            },
        )

    for source in budgets:
        profile["budgets"].append(
            {
                "ref": refs.next_budget(),
                "category_ref": lookup_ref(category_refs_by_id, required_int(source, "category_id"), "budget category"),
                "limit_cents": required_int(source, "limit_cents"),
                "period": require_one_of("budget period", required_text(source, "period"), {"weekly", "monthly"}),
                "currency_code": normalize_currency(required_text(source, "currency_code")),
                "notify_at_percent": required_int(source, "notify_at_percent"),
                "notifications_enabled": bool_value(source, "notifications_enabled", default=True),
                "last_notified_percent": required_int(source, "last_notified_percent", default=0),
                "last_notified_at_epoch_millis": optional_epoch_millis(source, "last_notified_at"),
                "created_at_epoch_millis": epoch_millis(required_text(source, "created_at")),
                "updated_at_epoch_millis": epoch_millis(required_text(source, "updated_at")),
            },
        )

    for source in recurring:
        profile["recurring_transactions"].append(
            {
                "ref": refs.next_recurring(),
                "account_ref": lookup_ref(account_refs_by_id, required_int(source, "account_id"), "recurring account"),
                "category_ref": lookup_ref(category_refs_by_id, required_int(source, "category_id"), "recurring category"),
                "type": require_one_of("recurring transaction type", required_text(source, "type"), {"expense", "income"}),
                "amount_cents": required_int(source, "amount_cents"),
                "currency_code": normalize_currency(required_text(source, "currency_code")),
                "note": optional_text(source, "note"),
                "frequency": require_one_of(
                    "recurring frequency",
                    required_text(source, "frequency"),
                    {"daily", "weekly", "monthly", "yearly"},
                ),
                "next_run_at_epoch_millis": epoch_millis(required_text(source, "next_run_at")),
                "is_active": bool_value(source, "is_active"),
                "created_at_epoch_millis": epoch_millis(required_text(source, "created_at")),
                "updated_at_epoch_millis": epoch_millis(required_text(source, "updated_at")),
            },
        )

    for source in savings_goals:
        account_id = optional_int(source, "account_id")
        ref = refs.next_goal()
        goal_refs_by_id[required_int(source, "id")] = ref
        profile["savings_goals"].append(
            {
                "ref": ref,
                "name": required_text(source, "name"),
                "target_cents": required_int(source, "target_cents"),
                "current_cents": required_int(source, "current_cents"),
                "currency_code": normalize_currency(required_text(source, "currency_code")),
                "deadline_date": date_string(optional_text(source, "deadline")) if optional_text(source, "deadline") else None,
                "account_ref": optional_lookup_ref(account_refs_by_id, account_id, "savings goal account"),
                "created_at_epoch_millis": epoch_millis(required_text(source, "created_at")),
                "updated_at_epoch_millis": epoch_millis(required_text(source, "updated_at")),
            },
        )

    for source in goal_transactions:
        profile["goal_transactions"].append(
            {
                "ref": refs.next_goal_transaction(),
                "goal_ref": lookup_ref(goal_refs_by_id, required_int(source, "goal_id"), "goal transaction goal"),
                "type": require_one_of("goal transaction type", required_text(source, "type"), {"deposit", "withdraw"}),
                "amount_cents": required_int(source, "amount_cents"),
                "created_at_epoch_millis": epoch_millis(required_text(source, "created_at")),
            },
        )

    for source in templates:
        profile["transaction_templates"].append(
            {
                "ref": refs.next_template(),
                "name": optional_text(source, "name"),
                "type": require_one_of("transaction template type", required_text(source, "type"), {"expense", "income"}),
                "amount_cents": required_int(source, "amount_cents"),
                "amount_fixed": bool_value(source, "amount_fixed"),
                "category_ref": lookup_ref(
                    category_refs_by_id,
                    required_int(source, "category_id"),
                    "transaction template category",
                ),
                "account_ref": lookup_ref(
                    account_refs_by_id,
                    required_int(source, "account_id"),
                    "transaction template account",
                ),
                "currency_code": normalize_currency(required_text(source, "currency_code")),
                "note": optional_text(source, "note"),
                "sort_order": required_int(source, "sort_order"),
                "created_at_epoch_millis": epoch_millis(required_text(source, "created_at")),
                "updated_at_epoch_millis": epoch_millis(required_text(source, "updated_at")),
            },
        )

    return profile


def included_categories(
    tables: dict[str, Table],
    user_id: int,
    transactions: list[dict[str, str | None]],
    budgets: list[dict[str, str | None]],
    recurring: list[dict[str, str | None]],
    templates: list[dict[str, str | None]],
) -> list[dict[str, str | None]]:
    referenced_category_ids = {
        required_int(row, "category_id")
        for rows in (transactions, budgets, recurring, templates)
        for row in rows
    }
    result = []
    for row in table_rows(tables, "categories"):
        category_user_id = optional_int(row, "user_id")
        if (
            category_user_id == user_id
            or bool_value(row, "is_protected", default=False)
            or required_int(row, "id") in referenced_category_ids
        ):
            result.append(row)
    return sorted(result, key=lambda row: required_int(row, "id"))


def build_exchange_rate_snapshots(tables: dict[str, Table]) -> list[dict[str, Any]]:
    rows = sorted(
        table_rows(tables, "exchange_rate_snapshots"),
        key=lambda row: (
            date_string(required_text(row, "snapshot_date")),
            normalize_currency(required_text(row, "base_currency")),
            normalize_currency(required_text(row, "target_currency")),
        ),
    )
    return [
        {
            "snapshot_date": date_string(required_text(row, "snapshot_date")),
            "base_currency_code": normalize_currency(required_text(row, "base_currency")),
            "target_currency_code": normalize_currency(required_text(row, "target_currency")),
            "rate_e8": decimal_rate_to_e8(required_text(row, "rate")),
            "created_at_epoch_millis": epoch_millis(required_text(row, "created_at")),
        }
        for row in rows
    ]


def table_rows(tables: dict[str, Table], name: str) -> list[dict[str, str | None]]:
    table = tables.get(name)
    return [] if table is None else table.rows


def rows_for_user(tables: dict[str, Table], name: str, user_id: int) -> list[dict[str, str | None]]:
    return [row for row in table_rows(tables, name) if optional_int(row, "user_id") == user_id]


def lookup_ref(refs: dict[int, str], value: int, scope: str) -> str:
    try:
        return refs[value]
    except KeyError as error:
        raise ConversionError(f"missing {scope} ref for selected data") from error


def optional_lookup_ref(refs: dict[int, str], value: int | None, scope: str) -> str | None:
    if value is None:
        return None
    return lookup_ref(refs, value, scope)


def required_text(row: dict[str, str | None], column: str) -> str:
    if column not in row:
        raise ConversionError(f"required column is missing: {column}")
    value = row[column]
    if value is None:
        raise ConversionError(f"required column is null: {column}")
    return value


def optional_text(row: dict[str, str | None], column: str) -> str:
    value = row.get(column)
    return "" if value is None else value


def required_int(row: dict[str, str | None], column: str, default: int | None = None) -> int:
    value = row.get(column)
    if value is None or value == "":
        if default is not None:
            return default
        raise ConversionError(f"required integer column is missing or null: {column}")
    try:
        return int(value)
    except ValueError as error:
        raise ConversionError(f"invalid integer value in column: {column}") from error


def optional_int(row: dict[str, str | None], column: str) -> int | None:
    value = row.get(column)
    if value is None or value == "":
        return None
    try:
        return int(value)
    except ValueError as error:
        raise ConversionError(f"invalid integer value in column: {column}") from error


def bool_value(row: dict[str, str | None], column: str, default: bool | None = None) -> bool:
    value = row.get(column)
    if value is None or value == "":
        if default is None:
            raise ConversionError(f"required boolean column is missing or null: {column}")
        return default
    normalized = value.strip().lower()
    if normalized in {"t", "true", "1", "yes", "y"}:
        return True
    if normalized in {"f", "false", "0", "no", "n"}:
        return False
    raise ConversionError(f"invalid boolean value in column: {column}")


def optional_bool(row: dict[str, str | None], column: str) -> bool | None:
    value = row.get(column)
    if value is None or value == "":
        return None
    return bool_value(row, column)


def parse_display_currencies(value: str) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for item in value.split(","):
        code = normalize_currency(item)
        if code and code not in seen:
            seen.add(code)
            result.append(code)
    return result


def normalize_currency(value: str) -> str:
    return value.strip().upper()


def normalize_username(value: str) -> str:
    return value.strip().lstrip("@").casefold()


def normalized_default(value: str, fallback: str) -> str:
    value = value.strip()
    return value if value else fallback


def require_one_of(scope: str, value: str, allowed: set[str]) -> str:
    if value not in allowed:
        raise ConversionError(f"unsupported {scope}")
    return value


def decimal_rate_to_e8(value: str) -> int:
    try:
        scaled = Decimal(value) * RATE_SCALE_E8
    except Exception as error:
        raise ConversionError("invalid decimal exchange rate") from error
    return int(scaled.to_integral_value(rounding=ROUND_HALF_UP))


def epoch_millis(value: str) -> int:
    return int(parse_datetime(value).timestamp() * 1000)


def optional_epoch_millis(row: dict[str, str | None], column: str) -> int | None:
    value = row.get(column)
    return None if value is None or value == "" else epoch_millis(value)


def timestamp_sort_key(row: dict[str, str | None], column: str) -> int:
    return epoch_millis(required_text(row, column))


def parse_datetime(value: str) -> datetime:
    normalized = value.strip()
    if not normalized:
        raise ConversionError("empty timestamp value")
    if normalized.endswith("Z"):
        normalized = normalized[:-1] + "+00:00"
    normalized = TIMEZONE_WITHOUT_MINUTES_RE.sub(r"\1:00", normalized)
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as error:
        try:
            parsed = datetime.fromisoformat(normalized + "T00:00:00+00:00")
        except ValueError:
            raise ConversionError("invalid timestamp value") from error
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def date_string(value: str, fallback_timestamp: str | None = None) -> str:
    if value:
        stripped = value.strip()
        if len(stripped) >= 10:
            return stripped[:10]
    if fallback_timestamp is None:
        raise ConversionError("date value is missing")
    return parse_datetime(fallback_timestamp).date().isoformat()


def encode_backup(backup: dict[str, Any]) -> str:
    return json.dumps(backup, ensure_ascii=False, indent=2) + "\n"


def scan_identity_fragments(
    encoded_backup: str,
    identity: dict[str, str],
    extra_fragments: list[str],
) -> None:
    fragments: list[tuple[str, str]] = []
    for key, value in identity.items():
        if value:
            fragments.append((key, value))
    fragments.extend(("forbidden-fragment", value) for value in extra_fragments if value)

    normalized_backup = encoded_backup.casefold()
    for label, fragment in fragments:
        if fragment.casefold() in normalized_backup:
            raise ConversionError(f"generated backup would contain a forbidden identity fragment: {label}")


def max_epoch_millis(value: Any) -> int:
    values: list[int] = []

    def visit(node: Any) -> None:
        if isinstance(node, dict):
            for key, child in node.items():
                if key.endswith("_epoch_millis") and isinstance(child, int):
                    values.append(child)
                visit(child)
        elif isinstance(node, list):
            for child in node:
                visit(child)

    visit(value)
    return max(values) if values else 0


def write_output(path: Path, encoded: str) -> None:
    if str(path) == "-":
        sys.stdout.write(encoded)
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(encoded, encoding="utf-8", newline="\n")
    make_private(path)


def write_totals(path: Path, backup: dict[str, Any]) -> None:
    try:
        from migration_rehearsal import compute_totals
    except ImportError as error:
        raise ConversionError("migration_rehearsal.py must be next to this converter to write totals") from error
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(compute_totals(backup), ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    make_private(path)


def make_private(path: Path) -> None:
    try:
        path.chmod(0o600)
    except OSError:
        pass


def print_summary(path: Path, backup: dict[str, Any]) -> None:
    profile = backup["profiles"][0]
    counts = {
        "accounts": len(profile["accounts"]),
        "categories": len(profile["categories"]),
        "transactions": len(profile["transactions"]),
        "transfers": len(profile["transfers"]),
        "budgets": len(profile["budgets"]),
        "recurring_transactions": len(profile["recurring_transactions"]),
        "savings_goals": len(profile["savings_goals"]),
        "goal_transactions": len(profile["goal_transactions"]),
        "transaction_templates": len(profile["transaction_templates"]),
        "exchange_rate_snapshots": len(backup["exchange_rate_snapshots"]),
    }
    print(f"backup_written={path}")
    print("counts=" + ",".join(f"{key}:{value}" for key, value in counts.items()))


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
