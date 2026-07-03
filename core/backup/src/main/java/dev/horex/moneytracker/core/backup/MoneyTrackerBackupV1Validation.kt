package dev.horex.moneytracker.core.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

data class MoneyTrackerBackupValidationResult(
    val issues: List<MoneyTrackerBackupValidationIssue>,
) {
    val errors: List<MoneyTrackerBackupValidationIssue>
        get() = issues.filter { it.severity == MoneyTrackerBackupValidationSeverity.Error }

    val warnings: List<MoneyTrackerBackupValidationIssue>
        get() = issues.filter { it.severity == MoneyTrackerBackupValidationSeverity.Warning }

    val canImport: Boolean
        get() = errors.isEmpty()
}

data class MoneyTrackerBackupValidationIssue(
    val severity: MoneyTrackerBackupValidationSeverity,
    val code: MoneyTrackerBackupValidationCode,
    val path: String,
    val message: String,
)

enum class MoneyTrackerBackupValidationSeverity {
    Error,
    Warning,
}

enum class MoneyTrackerBackupValidationCode {
    InvalidJson,
    InvalidBackupShape,
    UnsupportedFormat,
    UnsupportedVersion,
    EmptyBackup,
    InvalidRef,
    DuplicateRef,
    ForbiddenRefPart,
    UnresolvedRef,
    ForbiddenSourceIdentityField,
}

object MoneyTrackerBackupV1Validator {
    private val refPattern = Regex("^[a-z][a-z0-9_]*(?:[-:.][a-z0-9_]+)*$")
    private val forbiddenRefParts = listOf("telegram", "source", "legacy", "init_data", "bot", "chat")
    private val camelBoundary = Regex("([a-z0-9])([A-Z])")
    private val lenientJson = Json(MoneyTrackerBackupV1Json.json) {
        ignoreUnknownKeys = true
    }

    private val forbiddenIdentityReasons = mapOf(
        "telegram" to "Telegram identity",
        "telegram_id" to "Telegram identity",
        "telegram_user_id" to "Telegram identity",
        "telegram_username" to "Telegram identity",
        "username" to "Telegram profile identity",
        "first_name" to "Telegram profile identity",
        "last_name" to "Telegram profile identity",
        "init_data" to "Telegram init data",
        "init_data_unsafe" to "Telegram init data",
        "auth_date" to "Telegram init data",
        "hash" to "Telegram init data",
        "query_id" to "Telegram init data",
        "bot" to "Telegram bot metadata",
        "bot_id" to "Telegram bot metadata",
        "chat" to "Telegram chat metadata",
        "chat_id" to "Telegram chat metadata",
        "legacy" to "legacy source identity",
        "legacy_id" to "legacy source identity",
        "source" to "source identity",
        "source_id" to "source identity",
        "source_db_id" to "source database identity",
    )

    fun validate(backup: MoneyTrackerBackup): MoneyTrackerBackupValidationResult {
        return validateBackup(backup, seedIssues = emptyList())
    }

    fun validateJson(value: String): MoneyTrackerBackupValidationResult {
        val element = try {
            MoneyTrackerBackupV1Json.json.parseToJsonElement(value)
        } catch (_: SerializationException) {
            return validationResult(
                listOf(
                    error(
                        code = MoneyTrackerBackupValidationCode.InvalidJson,
                        path = "$",
                        message = "Backup JSON cannot be parsed.",
                    ),
                ),
            )
        } catch (_: IllegalArgumentException) {
            return validationResult(
                listOf(
                    error(
                        code = MoneyTrackerBackupValidationCode.InvalidJson,
                        path = "$",
                        message = "Backup JSON cannot be parsed.",
                    ),
                ),
            )
        }

        val rawIssues = collectForbiddenIdentityFields(element)
        val strictDecode = runCatching {
            MoneyTrackerBackupV1Json.json.decodeFromJsonElement<MoneyTrackerBackup>(element)
        }
        val shapeIssue = strictDecode.exceptionOrNull()?.let {
            error(
                code = MoneyTrackerBackupValidationCode.InvalidBackupShape,
                path = "$",
                message = "Backup JSON does not match MoneyTrackerBackup v1.",
            )
        }
        val backup = strictDecode.getOrNull() ?: runCatching {
            lenientJson.decodeFromJsonElement<MoneyTrackerBackup>(element)
        }.getOrNull()

        return if (backup == null) {
            validationResult(rawIssues + listOfNotNull(shapeIssue))
        } else {
            validateBackup(backup, seedIssues = rawIssues + listOfNotNull(shapeIssue))
        }
    }

    private fun validateBackup(
        backup: MoneyTrackerBackup,
        seedIssues: List<MoneyTrackerBackupValidationIssue>,
    ): MoneyTrackerBackupValidationResult {
        val issues = seedIssues.toMutableList()
        if (backup.format != MONEY_TRACKER_BACKUP_FORMAT) {
            issues += error(
                code = MoneyTrackerBackupValidationCode.UnsupportedFormat,
                path = "$.format",
                message = "Unsupported backup format: ${backup.format}",
            )
        }
        if (backup.version != MONEY_TRACKER_BACKUP_VERSION) {
            issues += error(
                code = MoneyTrackerBackupValidationCode.UnsupportedVersion,
                path = "$.version",
                message = "Unsupported backup version: ${backup.version}",
            )
        }
        if (backup.profiles.isEmpty()) {
            issues += warning(
                code = MoneyTrackerBackupValidationCode.EmptyBackup,
                path = "$.profiles",
                message = "Backup contains no profiles; restore dry-run would import no profile data.",
            )
        }

        uniqueRefs(
            scope = "profiles",
            refs = backup.profiles.mapIndexed { index, profile ->
                BackupRef(profile.ref, "$.profiles[$index].ref")
            },
            issues = issues,
        )
        backup.profiles.forEachIndexed { index, profile ->
            validateProfile(profile, "$.profiles[$index]", issues)
        }

        return validationResult(issues)
    }

    private fun validateProfile(
        profile: BackupProfile,
        profilePath: String,
        issues: MutableList<MoneyTrackerBackupValidationIssue>,
    ) {
        val accountRefs = uniqueRefs(
            scope = "${profile.ref}.accounts",
            refs = profile.accounts.mapIndexed { index, account ->
                BackupRef(account.ref, "$profilePath.accounts[$index].ref")
            },
            issues = issues,
        )
        val categoryRefs = uniqueRefs(
            scope = "${profile.ref}.categories",
            refs = profile.categories.mapIndexed { index, category ->
                BackupRef(category.ref, "$profilePath.categories[$index].ref")
            },
            issues = issues,
        )
        val transactionRefs = uniqueRefs(
            scope = "${profile.ref}.transactions",
            refs = profile.transactions.mapIndexed { index, transaction ->
                BackupRef(transaction.ref, "$profilePath.transactions[$index].ref")
            },
            issues = issues,
        )
        val goalRefs = uniqueRefs(
            scope = "${profile.ref}.savings_goals",
            refs = profile.savingsGoals.mapIndexed { index, goal ->
                BackupRef(goal.ref, "$profilePath.savings_goals[$index].ref")
            },
            issues = issues,
        )

        uniqueRefs(
            scope = "${profile.ref}.transfers",
            refs = profile.transfers.mapIndexed { index, transfer ->
                BackupRef(transfer.ref, "$profilePath.transfers[$index].ref")
            },
            issues = issues,
        )
        uniqueRefs(
            scope = "${profile.ref}.budgets",
            refs = profile.budgets.mapIndexed { index, budget ->
                BackupRef(budget.ref, "$profilePath.budgets[$index].ref")
            },
            issues = issues,
        )
        uniqueRefs(
            scope = "${profile.ref}.recurring_transactions",
            refs = profile.recurringTransactions.mapIndexed { index, recurring ->
                BackupRef(recurring.ref, "$profilePath.recurring_transactions[$index].ref")
            },
            issues = issues,
        )
        uniqueRefs(
            scope = "${profile.ref}.goal_transactions",
            refs = profile.goalTransactions.mapIndexed { index, transaction ->
                BackupRef(transaction.ref, "$profilePath.goal_transactions[$index].ref")
            },
            issues = issues,
        )
        uniqueRefs(
            scope = "${profile.ref}.exchange_rate_overrides",
            refs = profile.exchangeRateOverrides.mapIndexed { index, override ->
                BackupRef(override.ref, "$profilePath.exchange_rate_overrides[$index].ref")
            },
            issues = issues,
        )
        uniqueRefs(
            scope = "${profile.ref}.transaction_templates",
            refs = profile.transactionTemplates.mapIndexed { index, template ->
                BackupRef(template.ref, "$profilePath.transaction_templates[$index].ref")
            },
            issues = issues,
        )

        profile.transactions.forEachIndexed { index, transaction ->
            requireResolvedRef(
                ref = transaction.accountRef,
                knownRefs = accountRefs,
                scope = "${profile.ref}.transactions.account_ref",
                path = "$profilePath.transactions[$index].account_ref",
                issues = issues,
            )
            requireResolvedRef(
                ref = transaction.categoryRef,
                knownRefs = categoryRefs,
                scope = "${profile.ref}.transactions.category_ref",
                path = "$profilePath.transactions[$index].category_ref",
                issues = issues,
            )
        }
        profile.transfers.forEachIndexed { index, transfer ->
            requireResolvedRef(
                ref = transfer.fromAccountRef,
                knownRefs = accountRefs,
                scope = "${profile.ref}.transfers.from_account_ref",
                path = "$profilePath.transfers[$index].from_account_ref",
                issues = issues,
            )
            requireResolvedRef(
                ref = transfer.toAccountRef,
                knownRefs = accountRefs,
                scope = "${profile.ref}.transfers.to_account_ref",
                path = "$profilePath.transfers[$index].to_account_ref",
                issues = issues,
            )
            transfer.fromTransactionRef?.let {
                requireResolvedRef(
                    ref = it,
                    knownRefs = transactionRefs,
                    scope = "${profile.ref}.transfers.from_transaction_ref",
                    path = "$profilePath.transfers[$index].from_transaction_ref",
                    issues = issues,
                )
            }
            transfer.toTransactionRef?.let {
                requireResolvedRef(
                    ref = it,
                    knownRefs = transactionRefs,
                    scope = "${profile.ref}.transfers.to_transaction_ref",
                    path = "$profilePath.transfers[$index].to_transaction_ref",
                    issues = issues,
                )
            }
        }
        profile.budgets.forEachIndexed { index, budget ->
            requireResolvedRef(
                ref = budget.categoryRef,
                knownRefs = categoryRefs,
                scope = "${profile.ref}.budgets.category_ref",
                path = "$profilePath.budgets[$index].category_ref",
                issues = issues,
            )
        }
        profile.recurringTransactions.forEachIndexed { index, recurring ->
            requireResolvedRef(
                ref = recurring.accountRef,
                knownRefs = accountRefs,
                scope = "${profile.ref}.recurring_transactions.account_ref",
                path = "$profilePath.recurring_transactions[$index].account_ref",
                issues = issues,
            )
            requireResolvedRef(
                ref = recurring.categoryRef,
                knownRefs = categoryRefs,
                scope = "${profile.ref}.recurring_transactions.category_ref",
                path = "$profilePath.recurring_transactions[$index].category_ref",
                issues = issues,
            )
        }
        profile.savingsGoals.forEachIndexed { index, goal ->
            goal.accountRef?.let {
                requireResolvedRef(
                    ref = it,
                    knownRefs = accountRefs,
                    scope = "${profile.ref}.savings_goals.account_ref",
                    path = "$profilePath.savings_goals[$index].account_ref",
                    issues = issues,
                )
            }
        }
        profile.goalTransactions.forEachIndexed { index, transaction ->
            requireResolvedRef(
                ref = transaction.goalRef,
                knownRefs = goalRefs,
                scope = "${profile.ref}.goal_transactions.goal_ref",
                path = "$profilePath.goal_transactions[$index].goal_ref",
                issues = issues,
            )
        }
        profile.transactionTemplates.forEachIndexed { index, template ->
            requireResolvedRef(
                ref = template.accountRef,
                knownRefs = accountRefs,
                scope = "${profile.ref}.transaction_templates.account_ref",
                path = "$profilePath.transaction_templates[$index].account_ref",
                issues = issues,
            )
            requireResolvedRef(
                ref = template.categoryRef,
                knownRefs = categoryRefs,
                scope = "${profile.ref}.transaction_templates.category_ref",
                path = "$profilePath.transaction_templates[$index].category_ref",
                issues = issues,
            )
        }
    }

    private fun uniqueRefs(
        scope: String,
        refs: List<BackupRef>,
        issues: MutableList<MoneyTrackerBackupValidationIssue>,
    ): Set<String> {
        val seen = linkedSetOf<String>()
        refs.forEach { item ->
            requireValidRef(scope, item, issues)
            if (!seen.add(item.value)) {
                issues += error(
                    code = MoneyTrackerBackupValidationCode.DuplicateRef,
                    path = item.path,
                    message = "Duplicate export ref '${item.value}' in $scope",
                )
            }
        }
        return seen
    }

    private fun requireValidRef(
        scope: String,
        item: BackupRef,
        issues: MutableList<MoneyTrackerBackupValidationIssue>,
    ) {
        if (!refPattern.matches(item.value)) {
            issues += error(
                code = MoneyTrackerBackupValidationCode.InvalidRef,
                path = item.path,
                message = "Invalid export ref '${item.value}' in $scope",
            )
        }
        val normalized = item.value.lowercase()
        val forbidden = forbiddenRefParts.firstOrNull { it in normalized }
        if (forbidden != null) {
            issues += error(
                code = MoneyTrackerBackupValidationCode.ForbiddenRefPart,
                path = item.path,
                message = "Forbidden export ref part '$forbidden' in $scope",
            )
        }
    }

    private fun requireResolvedRef(
        ref: String,
        knownRefs: Set<String>,
        scope: String,
        path: String,
        issues: MutableList<MoneyTrackerBackupValidationIssue>,
    ) {
        if (ref !in knownRefs) {
            issues += error(
                code = MoneyTrackerBackupValidationCode.UnresolvedRef,
                path = path,
                message = "Unresolved export ref '$ref' in $scope",
            )
        }
    }

    private fun collectForbiddenIdentityFields(
        element: JsonElement,
        path: String = "$",
    ): List<MoneyTrackerBackupValidationIssue> {
        return when (element) {
            is JsonObject -> element.entries
                .sortedBy { it.key }
                .flatMap { (key, value) ->
                    val childPath = "$path.$key"
                    val reason = forbiddenIdentityReason(key)
                    val ownIssue = if (reason == null) {
                        emptyList()
                    } else {
                        listOf(
                            error(
                                code = MoneyTrackerBackupValidationCode.ForbiddenSourceIdentityField,
                                path = childPath,
                                message = "Forbidden source identity field '$key' at $childPath ($reason).",
                            ),
                        )
                    }
                    ownIssue + collectForbiddenIdentityFields(value, childPath)
                }

            is JsonArray -> element.flatMapIndexed { index, value ->
                collectForbiddenIdentityFields(value, "$path[$index]")
            }

            else -> emptyList()
        }
    }

    private fun forbiddenIdentityReason(key: String): String? {
        val normalized = normalizeIdentityKey(key)
        forbiddenIdentityReasons[normalized]?.let { return it }
        val forbiddenPart = forbiddenRefParts.firstOrNull { part ->
            normalized == part ||
                normalized.startsWith("${part}_") ||
                normalized.endsWith("_$part") ||
                normalized.contains("_${part}_")
        }
        if (forbiddenPart != null) {
            return "source identity marker '$forbiddenPart'"
        }
        if (normalized == "id" || normalized.endsWith("_id")) {
            return "source database identity"
        }
        return null
    }

    private fun normalizeIdentityKey(key: String): String {
        return key
            .replace(camelBoundary, "$1_$2")
            .replace('-', '_')
            .lowercase()
    }

    private fun validationResult(
        issues: List<MoneyTrackerBackupValidationIssue>,
    ): MoneyTrackerBackupValidationResult {
        return MoneyTrackerBackupValidationResult(
            issues = issues
                .distinct()
                .sortedWith(
                    compareBy<MoneyTrackerBackupValidationIssue> { it.severity.ordinal }
                        .thenBy { it.path }
                        .thenBy { it.code.ordinal }
                        .thenBy { it.message },
                ),
        )
    }

    private fun error(
        code: MoneyTrackerBackupValidationCode,
        path: String,
        message: String,
    ): MoneyTrackerBackupValidationIssue {
        return MoneyTrackerBackupValidationIssue(
            severity = MoneyTrackerBackupValidationSeverity.Error,
            code = code,
            path = path,
            message = message,
        )
    }

    private fun warning(
        code: MoneyTrackerBackupValidationCode,
        path: String,
        message: String,
    ): MoneyTrackerBackupValidationIssue {
        return MoneyTrackerBackupValidationIssue(
            severity = MoneyTrackerBackupValidationSeverity.Warning,
            code = code,
            path = path,
            message = message,
        )
    }

    private data class BackupRef(
        val value: String,
        val path: String,
    )
}
