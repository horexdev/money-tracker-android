package dev.horex.moneytracker.core.backup

class MoneyTrackerBackupContractException(message: String) : IllegalArgumentException(message)

object MoneyTrackerBackupV1Contract {
    private val refPattern = Regex("^[a-z][a-z0-9_]*(?:[-:.][a-z0-9_]+)*$")
    private val forbiddenRefParts = listOf("telegram", "source", "legacy", "init_data", "bot", "chat")

    fun requireValidExportRefs(backup: MoneyTrackerBackup) {
        if (backup.format != MONEY_TRACKER_BACKUP_FORMAT) {
            throw MoneyTrackerBackupContractException("Unsupported backup format: ${backup.format}")
        }
        if (backup.version != MONEY_TRACKER_BACKUP_VERSION) {
            throw MoneyTrackerBackupContractException("Unsupported backup version: ${backup.version}")
        }

        uniqueRefs("profiles", backup.profiles.map { it.ref })
        backup.profiles.forEach { profile -> requireValidProfileRefs(profile) }
    }

    private fun requireValidProfileRefs(profile: BackupProfile) {
        val accountRefs = uniqueRefs("${profile.ref}.accounts", profile.accounts.map { it.ref })
        val categoryRefs = uniqueRefs("${profile.ref}.categories", profile.categories.map { it.ref })
        val transactionRefs = uniqueRefs("${profile.ref}.transactions", profile.transactions.map { it.ref })
        val goalRefs = uniqueRefs("${profile.ref}.savings_goals", profile.savingsGoals.map { it.ref })

        uniqueRefs("${profile.ref}.transfers", profile.transfers.map { it.ref })
        uniqueRefs("${profile.ref}.budgets", profile.budgets.map { it.ref })
        uniqueRefs("${profile.ref}.recurring_transactions", profile.recurringTransactions.map { it.ref })
        uniqueRefs("${profile.ref}.goal_transactions", profile.goalTransactions.map { it.ref })
        uniqueRefs("${profile.ref}.exchange_rate_overrides", profile.exchangeRateOverrides.map { it.ref })
        uniqueRefs("${profile.ref}.transaction_templates", profile.transactionTemplates.map { it.ref })

        profile.transactions.forEach { transaction ->
            requireResolvedRef(transaction.accountRef, accountRefs, "${profile.ref}.transactions.account_ref")
            requireResolvedRef(transaction.categoryRef, categoryRefs, "${profile.ref}.transactions.category_ref")
        }
        profile.transfers.forEach { transfer ->
            requireResolvedRef(transfer.fromAccountRef, accountRefs, "${profile.ref}.transfers.from_account_ref")
            requireResolvedRef(transfer.toAccountRef, accountRefs, "${profile.ref}.transfers.to_account_ref")
            transfer.fromTransactionRef?.let {
                requireResolvedRef(it, transactionRefs, "${profile.ref}.transfers.from_transaction_ref")
            }
            transfer.toTransactionRef?.let {
                requireResolvedRef(it, transactionRefs, "${profile.ref}.transfers.to_transaction_ref")
            }
        }
        profile.budgets.forEach { budget ->
            requireResolvedRef(budget.categoryRef, categoryRefs, "${profile.ref}.budgets.category_ref")
        }
        profile.recurringTransactions.forEach { recurring ->
            requireResolvedRef(recurring.accountRef, accountRefs, "${profile.ref}.recurring_transactions.account_ref")
            requireResolvedRef(recurring.categoryRef, categoryRefs, "${profile.ref}.recurring_transactions.category_ref")
        }
        profile.savingsGoals.forEach { goal ->
            goal.accountRef?.let { requireResolvedRef(it, accountRefs, "${profile.ref}.savings_goals.account_ref") }
        }
        profile.goalTransactions.forEach { transaction ->
            requireResolvedRef(transaction.goalRef, goalRefs, "${profile.ref}.goal_transactions.goal_ref")
        }
        profile.transactionTemplates.forEach { template ->
            requireResolvedRef(template.accountRef, accountRefs, "${profile.ref}.transaction_templates.account_ref")
            requireResolvedRef(template.categoryRef, categoryRefs, "${profile.ref}.transaction_templates.category_ref")
        }
    }

    private fun uniqueRefs(scope: String, refs: List<String>): Set<String> {
        val seen = mutableSetOf<String>()
        refs.forEach { ref ->
            requireValidRef(scope, ref)
            if (!seen.add(ref)) {
                throw MoneyTrackerBackupContractException("Duplicate export ref '$ref' in $scope")
            }
        }
        return seen
    }

    private fun requireValidRef(scope: String, ref: String) {
        if (!refPattern.matches(ref)) {
            throw MoneyTrackerBackupContractException("Invalid export ref '$ref' in $scope")
        }
        val normalized = ref.lowercase()
        val forbidden = forbiddenRefParts.firstOrNull { it in normalized }
        if (forbidden != null) {
            throw MoneyTrackerBackupContractException("Forbidden export ref part '$forbidden' in $scope")
        }
    }

    private fun requireResolvedRef(ref: String, knownRefs: Set<String>, scope: String) {
        if (ref !in knownRefs) {
            throw MoneyTrackerBackupContractException("Unresolved export ref '$ref' in $scope")
        }
    }
}
