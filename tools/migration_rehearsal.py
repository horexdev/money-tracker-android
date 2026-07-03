#!/usr/bin/env python3
"""Validate a MoneyTrackerBackup export for migration rehearsal."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any


REF_PATTERN = re.compile(r"^[a-z][a-z0-9_]*(?:[-:.][a-z0-9_]+)*$")
CAMEL_BOUNDARY = re.compile(r"([a-z0-9])([A-Z])")
FORBIDDEN_REF_PARTS = ("telegram", "source", "legacy", "init_data", "bot", "chat")
EXACT_FORBIDDEN_KEYS = {
    "telegram",
    "telegram_id",
    "telegram_user_id",
    "telegram_username",
    "username",
    "first_name",
    "last_name",
    "init_data",
    "init_data_unsafe",
    "auth_date",
    "hash",
    "query_id",
    "bot",
    "bot_id",
    "chat",
    "chat_id",
    "legacy",
    "legacy_id",
    "source",
    "source_id",
    "source_db_id",
}
FORBIDDEN_VALUE_FRAGMENTS = (
    "telegram",
    "initdata",
    "init_data",
    "legacy",
    "source",
    "sqlcipher",
    "passphrase",
    "keystore",
    "device-bound",
    "device_bound",
    "device bound",
    "secret",
    "password",
)
BOT_OR_CHAT_METADATA_PATTERN = re.compile(r"(^|[^a-z0-9])(bot|chat)([^a-z0-9]|$)")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run deterministic migration rehearsal checks over a MoneyTrackerBackup JSON file.",
    )
    parser.add_argument("--backup", required=True, type=Path, help="Path to MoneyTrackerBackup JSON.")
    parser.add_argument("--expected", type=Path, help="Optional expected totals JSON to compare against.")
    parser.add_argument(
        "--forbidden-fragment",
        action="append",
        default=[],
        help="Additional raw fragment that must not appear in the backup. Repeatable.",
    )
    parser.add_argument("--markdown", action="store_true", help="Print a compact markdown report.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        backup_text = args.backup.read_text(encoding="utf-8")
        backup = json.loads(backup_text)
    except OSError as error:
        print(f"failed to read backup: {error}", file=sys.stderr)
        return 2
    except json.JSONDecodeError as error:
        print(f"failed to parse backup JSON: {error}", file=sys.stderr)
        return 2

    issues = validate_shape(backup)
    issues.extend(scan_forbidden_identity(backup, backup_text, args.forbidden_fragment))
    totals = compute_totals(backup)

    expected = None
    if args.expected is not None:
        try:
            expected = json.loads(args.expected.read_text(encoding="utf-8"))
        except OSError as error:
            print(f"failed to read expected totals: {error}", file=sys.stderr)
            return 2
        except json.JSONDecodeError as error:
            print(f"failed to parse expected totals JSON: {error}", file=sys.stderr)
            return 2
        if totals != expected:
            issues.append("financial totals do not match expected totals JSON")

    if args.markdown:
        print_markdown_report(backup, totals, expected, issues)
    else:
        print(json.dumps({"totals": totals, "issues": issues}, indent=2, sort_keys=True))

    return 1 if issues else 0


def validate_shape(backup: dict[str, Any]) -> list[str]:
    issues: list[str] = []
    if backup.get("format") != "MoneyTrackerBackup":
        issues.append(f"unsupported format: {backup.get('format')!r}")
    if backup.get("version") != 1:
        issues.append(f"unsupported version: {backup.get('version')!r}")
    if not isinstance(backup.get("profiles"), list):
        issues.append("profiles must be an array")
    if not isinstance(backup.get("exchange_rate_snapshots", []), list):
        issues.append("exchange_rate_snapshots must be an array")
    return issues


def scan_forbidden_identity(
    backup: Any,
    backup_text: str,
    forbidden_fragments: list[str],
) -> list[str]:
    issues: list[str] = []
    for path, key in iter_keys(backup):
        if is_forbidden_identity_key(key):
            issues.append(f"forbidden identity key at {path}: {key}")

    for path, value in iter_strings(backup):
        normalized = value.lower()
        if any(fragment in normalized for fragment in FORBIDDEN_VALUE_FRAGMENTS):
            issues.append(f"forbidden identity value at {path}: {value}")
        if BOT_OR_CHAT_METADATA_PATTERN.search(normalized):
            issues.append(f"bot/chat metadata value at {path}: {value}")

    for path, ref in iter_refs(backup):
        if not REF_PATTERN.match(ref):
            issues.append(f"invalid export ref at {path}: {ref}")
        normalized = ref.lower()
        forbidden_part = next((part for part in FORBIDDEN_REF_PARTS if part in normalized), None)
        if forbidden_part is not None:
            issues.append(f"forbidden export ref part '{forbidden_part}' at {path}: {ref}")

    for fragment in forbidden_fragments:
        if fragment and fragment.lower() in backup_text.lower():
            issues.append(f"forbidden raw fragment present: {fragment}")

    return sorted(set(issues))


def iter_keys(value: Any, path: str = "$"):
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            yield child_path, key
            yield from iter_keys(child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from iter_keys(child, f"{path}[{index}]")


def iter_strings(value: Any, path: str = "$"):
    if isinstance(value, dict):
        for key, child in value.items():
            yield from iter_strings(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from iter_strings(child, f"{path}[{index}]")
    elif isinstance(value, str):
        yield path, value


def iter_refs(value: Any, path: str = "$"):
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if isinstance(child, str) and (key == "ref" or key.endswith("_ref")):
                yield child_path, child
            yield from iter_refs(child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from iter_refs(child, f"{path}[{index}]")


def is_forbidden_identity_key(key: str) -> bool:
    normalized = normalize_identity_token(key)
    if normalized == "id" or normalized.endswith("_id"):
        return True
    if normalized in EXACT_FORBIDDEN_KEYS:
        return True
    return any(
        normalized == part
        or normalized.startswith(f"{part}_")
        or normalized.endswith(f"_{part}")
        or f"_{part}_" in normalized
        for part in FORBIDDEN_REF_PARTS
    )


def normalize_identity_token(key: str) -> str:
    return CAMEL_BOUNDARY.sub(r"\1_\2", key).replace("-", "_").lower()


def compute_totals(backup: dict[str, Any]) -> dict[str, Any]:
    profiles = {}
    for profile in backup.get("profiles", []):
        profile_ref = profile["ref"]
        profiles[profile_ref] = compute_profile_totals(profile)
    return {
        "profiles": profiles,
        "global": {
            "exchange_rate_snapshot_count": len(backup.get("exchange_rate_snapshots", [])),
        },
    }


def compute_profile_totals(profile: dict[str, Any]) -> dict[str, Any]:
    accounts_by_ref = {account["ref"]: account for account in profile.get("accounts", [])}
    transaction_totals = nested_amounts()
    account_balances = defaultdict(lambda: defaultdict(int))
    for transaction in profile.get("transactions", []):
        currency = transaction["currency_code"]
        amount = int(transaction["amount_cents"])
        if transaction["type"] == "income":
            transaction_totals[currency]["income_cents"] += amount
            transaction_totals[currency]["net_cents"] += amount
            account_balances[transaction["account_ref"]][currency] += amount
        elif transaction["type"] == "expense":
            transaction_totals[currency]["expense_cents"] += amount
            transaction_totals[currency]["net_cents"] -= amount
            account_balances[transaction["account_ref"]][currency] -= amount

    included_account_totals = defaultdict(int)
    for account_ref, balances in account_balances.items():
        if accounts_by_ref.get(account_ref, {}).get("include_in_total", False):
            for currency, amount in balances.items():
                included_account_totals[currency] += amount

    transfer_totals = defaultdict(lambda: {"transfer_count": 0, "from_amount_cents": 0, "to_amount_cents": 0})
    transactions_by_ref = {transaction["ref"]: transaction for transaction in profile.get("transactions", [])}
    for transfer in profile.get("transfers", []):
        pair = f"{transfer['from_currency_code']}->{transfer['to_currency_code']}"
        transfer_totals[pair]["transfer_count"] += 1
        transfer_totals[pair]["from_amount_cents"] += int(transfer["amount_cents"])
        to_transaction_ref = transfer.get("to_transaction_ref")
        if to_transaction_ref is not None and to_transaction_ref in transactions_by_ref:
            transfer_totals[pair]["to_amount_cents"] += int(transactions_by_ref[to_transaction_ref]["amount_cents"])

    budget_limits = defaultdict(int)
    for budget in profile.get("budgets", []):
        budget_limits[budget["currency_code"]] += int(budget["limit_cents"])

    recurring_totals = nested_amounts()
    for recurring in profile.get("recurring_transactions", []):
        currency = recurring["currency_code"]
        amount = int(recurring["amount_cents"])
        if recurring["type"] == "income":
            recurring_totals[currency]["income_cents"] += amount
            recurring_totals[currency]["net_cents"] += amount
        elif recurring["type"] == "expense":
            recurring_totals[currency]["expense_cents"] += amount
            recurring_totals[currency]["net_cents"] -= amount

    savings_current = defaultdict(int)
    goals_by_ref = {goal["ref"]: goal for goal in profile.get("savings_goals", [])}
    for goal in profile.get("savings_goals", []):
        savings_current[goal["currency_code"]] += int(goal["current_cents"])

    goal_transaction_totals = nested_amounts()
    for transaction in profile.get("goal_transactions", []):
        goal = goals_by_ref[transaction["goal_ref"]]
        currency = goal["currency_code"]
        amount = int(transaction["amount_cents"])
        if transaction["type"] == "deposit":
            goal_transaction_totals[currency]["income_cents"] += amount
            goal_transaction_totals[currency]["net_cents"] += amount
        elif transaction["type"] == "withdraw":
            goal_transaction_totals[currency]["expense_cents"] += amount
            goal_transaction_totals[currency]["net_cents"] -= amount

    return {
        "counts": {
            "accounts": len(profile.get("accounts", [])),
            "categories": len(profile.get("categories", [])),
            "transactions": len(profile.get("transactions", [])),
            "transfers": len(profile.get("transfers", [])),
            "budgets": len(profile.get("budgets", [])),
            "recurring_transactions": len(profile.get("recurring_transactions", [])),
            "savings_goals": len(profile.get("savings_goals", [])),
            "goal_transactions": len(profile.get("goal_transactions", [])),
            "exchange_rate_overrides": len(profile.get("exchange_rate_overrides", [])),
            "transaction_templates": len(profile.get("transaction_templates", [])),
        },
        "transaction_totals_by_currency": sorted_amounts(transaction_totals),
        "included_account_totals_by_currency": dict(sorted(included_account_totals.items())),
        "transfer_totals_by_pair": dict(sorted(transfer_totals.items())),
        "budget_limits_by_currency": dict(sorted(budget_limits.items())),
        "recurring_totals_by_currency": sorted_amounts(recurring_totals),
        "savings_current_by_currency": dict(sorted(savings_current.items())),
        "goal_transaction_totals_by_currency": sorted_amounts(goal_transaction_totals),
    }


def nested_amounts():
    return defaultdict(lambda: {"income_cents": 0, "expense_cents": 0, "net_cents": 0})


def sorted_amounts(values: dict[str, dict[str, int]]) -> dict[str, dict[str, int]]:
    return {currency: values[currency] for currency in sorted(values)}


def print_markdown_report(
    backup: dict[str, Any],
    totals: dict[str, Any],
    expected: dict[str, Any] | None,
    issues: list[str],
) -> None:
    profiles = backup.get("profiles", [])
    print("# Migration rehearsal check")
    print()
    print(f"- Format: `{backup.get('format')}`")
    print(f"- Version: `{backup.get('version')}`")
    print(f"- Profiles: `{len(profiles)}`")
    print(f"- Exchange rate snapshots: `{len(backup.get('exchange_rate_snapshots', []))}`")
    print(f"- Identifier scan: `{'passed' if not any('identity' in issue or 'fragment' in issue or 'ref' in issue for issue in issues) else 'failed'}`")
    if expected is not None:
        print(f"- Totals comparison: `{'passed' if totals == expected else 'failed'}`")
    else:
        print("- Totals comparison: `not compared`")
    print(f"- Overall result: `{'passed' if not issues else 'failed'}`")
    print()
    for profile_ref, profile_totals in totals["profiles"].items():
        print(f"## {profile_ref}")
        print()
        print("| Currency | Income | Expense | Net | Included account total |")
        print("| --- | ---: | ---: | ---: | ---: |")
        currencies = sorted(
            set(profile_totals["transaction_totals_by_currency"])
            | set(profile_totals["included_account_totals_by_currency"]),
        )
        for currency in currencies:
            transaction = profile_totals["transaction_totals_by_currency"].get(
                currency,
                {"income_cents": 0, "expense_cents": 0, "net_cents": 0},
            )
            included_total = profile_totals["included_account_totals_by_currency"].get(currency, 0)
            print(
                f"| {currency} | {transaction['income_cents']} | {transaction['expense_cents']} | "
                f"{transaction['net_cents']} | {included_total} |",
            )
        print()
    if issues:
        print("## Issues")
        print()
        for issue in issues:
            print(f"- {issue}")


if __name__ == "__main__":
    raise SystemExit(main())
