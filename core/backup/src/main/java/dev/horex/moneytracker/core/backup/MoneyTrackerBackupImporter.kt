package dev.horex.moneytracker.core.backup

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.BudgetEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateOverrideEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateSnapshotEntity
import dev.horex.moneytracker.core.database.model.GoalTransactionEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionEntity
import dev.horex.moneytracker.core.database.model.SavingsGoalEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.model.TransactionTemplateEntity
import dev.horex.moneytracker.core.database.model.TransferEntity
import dev.horex.moneytracker.core.database.profile.normalizeLocalProfileLanguageCode
import java.util.Locale

class MoneyTrackerBackupImporter internal constructor(
    private val store: MoneyTrackerBackupImportStore,
) {
    constructor(database: MoneyTrackerDatabase) : this(RoomMoneyTrackerBackupImportStore(database))

    suspend fun importBackup(
        backup: MoneyTrackerBackup,
        options: MoneyTrackerBackupImportOptions = MoneyTrackerBackupImportOptions(),
    ): MoneyTrackerBackupImportResult {
        val validation = MoneyTrackerBackupV1Validator.validate(backup)
        if (!validation.canImport) {
            throw MoneyTrackerBackupImportValidationException(validation)
        }

        val profiles = backup.selectedProfiles(options)
        if (profiles.isEmpty()) {
            return MoneyTrackerBackupImportResult(
                importedProfiles = emptyList(),
                importedExchangeRateSnapshots = 0,
            )
        }

        return store.withTransaction {
            backup.exchangeRateSnapshots.forEach { snapshot ->
                saveExchangeRateSnapshot(snapshot)
            }
            val importedProfiles = profiles.map { profile ->
                importProfile(profile)
            }
            MoneyTrackerBackupImportResult(
                importedProfiles = importedProfiles,
                importedExchangeRateSnapshots = backup.exchangeRateSnapshots.size,
            )
        }
    }

    private suspend fun MoneyTrackerBackupImportStore.importProfile(
        profile: BackupProfile,
    ): MoneyTrackerBackupImportedProfile {
        val profileId = insertProfile(profile)
        val accountIds = mutableMapOf<String, Long>()
        val categoryIds = mutableMapOf<String, Long>()
        val transactionIds = mutableMapOf<String, Long>()
        val savingsGoalIds = mutableMapOf<String, Long>()

        profile.accounts.forEach { account ->
            accountIds[account.ref] = insertAccount(profileId, account)
        }
        profile.categories.forEach { category ->
            categoryIds[category.ref] = insertCategory(profileId, category)
        }
        profile.transactions.forEach { transaction ->
            transactionIds[transaction.ref] = insertTransaction(
                profileId = profileId,
                transaction = transaction,
                accountId = accountIds.requireLocalId(transaction.accountRef),
                categoryId = categoryIds.requireLocalId(transaction.categoryRef),
            )
        }
        profile.transfers.forEach { transfer ->
            insertTransfer(
                profileId = profileId,
                transfer = transfer,
                fromAccountId = accountIds.requireLocalId(transfer.fromAccountRef),
                toAccountId = accountIds.requireLocalId(transfer.toAccountRef),
                fromTransactionId = transfer.fromTransactionRef?.let(transactionIds::requireLocalId),
                toTransactionId = transfer.toTransactionRef?.let(transactionIds::requireLocalId),
            )
        }
        profile.budgets.forEach { budget ->
            insertBudget(
                profileId = profileId,
                budget = budget,
                categoryId = categoryIds.requireLocalId(budget.categoryRef),
            )
        }
        profile.recurringTransactions.forEach { recurring ->
            insertRecurringTransaction(
                profileId = profileId,
                recurring = recurring,
                accountId = accountIds.requireLocalId(recurring.accountRef),
                categoryId = categoryIds.requireLocalId(recurring.categoryRef),
            )
        }
        profile.savingsGoals.forEach { goal ->
            savingsGoalIds[goal.ref] = insertSavingsGoal(
                profileId = profileId,
                goal = goal,
                accountId = goal.accountRef?.let(accountIds::requireLocalId),
            )
        }
        profile.goalTransactions.forEach { transaction ->
            insertGoalTransaction(
                profileId = profileId,
                transaction = transaction,
                goalId = savingsGoalIds.requireLocalId(transaction.goalRef),
            )
        }
        profile.exchangeRateOverrides.forEach { override ->
            saveExchangeRateOverride(profileId, override)
        }
        profile.transactionTemplates.forEach { template ->
            insertTransactionTemplate(
                profileId = profileId,
                template = template,
                accountId = accountIds.requireLocalId(template.accountRef),
                categoryId = categoryIds.requireLocalId(template.categoryRef),
            )
        }

        return MoneyTrackerBackupImportedProfile(
            localProfileId = profileId,
            counts = MoneyTrackerBackupImportedProfileCounts(
                accounts = profile.accounts.size,
                categories = profile.categories.size,
                transactions = profile.transactions.size,
                transfers = profile.transfers.size,
                budgets = profile.budgets.size,
                recurringTransactions = profile.recurringTransactions.size,
                savingsGoals = profile.savingsGoals.size,
                goalTransactions = profile.goalTransactions.size,
                exchangeRateOverrides = profile.exchangeRateOverrides.size,
                transactionTemplates = profile.transactionTemplates.size,
            ),
        )
    }
}

data class MoneyTrackerBackupImportOptions(
    val selectedProfileRefs: Set<String>? = null,
)

data class MoneyTrackerBackupImportResult(
    val importedProfiles: List<MoneyTrackerBackupImportedProfile>,
    val importedExchangeRateSnapshots: Int,
) {
    val importedProfileCount: Int
        get() = importedProfiles.size
}

data class MoneyTrackerBackupImportedProfile(
    val localProfileId: Long,
    val counts: MoneyTrackerBackupImportedProfileCounts,
)

data class MoneyTrackerBackupImportedProfileCounts(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val transfers: Int,
    val budgets: Int,
    val recurringTransactions: Int,
    val savingsGoals: Int,
    val goalTransactions: Int,
    val exchangeRateOverrides: Int,
    val transactionTemplates: Int,
)

open class MoneyTrackerBackupImportException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class MoneyTrackerBackupImportValidationException(
    val validationResult: MoneyTrackerBackupValidationResult,
) : MoneyTrackerBackupImportException("Backup validation failed before import.")

class MoneyTrackerBackupImportSelectionException(
    message: String,
) : MoneyTrackerBackupImportException(message)

internal interface MoneyTrackerBackupImportStore {
    suspend fun <T> withTransaction(block: suspend MoneyTrackerBackupImportStore.() -> T): T
    suspend fun insertProfile(profile: BackupProfile): Long
    suspend fun insertAccount(profileId: Long, account: BackupAccount): Long
    suspend fun insertCategory(profileId: Long, category: BackupCategory): Long
    suspend fun insertTransaction(
        profileId: Long,
        transaction: BackupTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long

    suspend fun insertTransfer(
        profileId: Long,
        transfer: BackupTransfer,
        fromAccountId: Long,
        toAccountId: Long,
        fromTransactionId: Long?,
        toTransactionId: Long?,
    ): Long

    suspend fun insertBudget(profileId: Long, budget: BackupBudget, categoryId: Long): Long
    suspend fun insertRecurringTransaction(
        profileId: Long,
        recurring: BackupRecurringTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long

    suspend fun insertSavingsGoal(profileId: Long, goal: BackupSavingsGoal, accountId: Long?): Long
    suspend fun insertGoalTransaction(profileId: Long, transaction: BackupGoalTransaction, goalId: Long): Long
    suspend fun saveExchangeRateSnapshot(snapshot: BackupExchangeRateSnapshot): Long
    suspend fun saveExchangeRateOverride(profileId: Long, override: BackupExchangeRateOverride): Long
    suspend fun insertTransactionTemplate(
        profileId: Long,
        template: BackupTransactionTemplate,
        accountId: Long,
        categoryId: Long,
    ): Long
}

private class RoomMoneyTrackerBackupImportStore(
    private val database: MoneyTrackerDatabase,
) : MoneyTrackerBackupImportStore {
    private val localProfileDao = database.localProfileDao()
    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val transactionDao = database.transactionDao()
    private val transferDao = database.transferDao()
    private val budgetDao = database.budgetDao()
    private val recurringTransactionDao = database.recurringTransactionDao()
    private val savingsGoalDao = database.savingsGoalDao()
    private val goalTransactionDao = database.goalTransactionDao()
    private val exchangeRateSnapshotDao = database.exchangeRateSnapshotDao()
    private val exchangeRateOverrideDao = database.exchangeRateOverrideDao()
    private val transactionTemplateDao = database.transactionTemplateDao()

    override suspend fun <T> withTransaction(
        block: suspend MoneyTrackerBackupImportStore.() -> T,
    ): T {
        return database.withTransaction { this@RoomMoneyTrackerBackupImportStore.block() }
    }

    override suspend fun insertProfile(profile: BackupProfile): Long {
        return localProfileDao.insert(
            LocalProfileEntity(
                label = profile.label.trim(),
                languageCode = normalizeLocalProfileLanguageCode(profile.languageCode),
                displayCurrenciesCsv = profile.displayCurrencyCodes.toCurrencyCsv(),
                notifyBudgetAlerts = profile.notificationPreferences.notifyBudgetAlerts,
                notifyRecurringReminders = profile.notificationPreferences.notifyRecurringReminders,
                notifyWeeklySummary = profile.notificationPreferences.notifyWeeklySummary,
                notifyGoalMilestones = profile.notificationPreferences.notifyGoalMilestones,
                statsChartStyle = profile.uiPreferences.statsChartStyle.storageValue,
                animateNumbers = profile.uiPreferences.animateNumbers,
                theme = profile.uiPreferences.theme.storageValue,
                hideAmounts = profile.uiPreferences.hideAmounts,
                createdAtEpochMillis = profile.createdAtEpochMillis,
                updatedAtEpochMillis = profile.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun insertAccount(profileId: Long, account: BackupAccount): Long {
        return accountDao.insert(
            AccountEntity(
                profileId = profileId,
                name = account.name,
                icon = account.icon,
                color = account.color,
                type = account.type.storageValue,
                currencyCode = account.currencyCode,
                isDefault = account.isDefault,
                includeInTotal = account.includeInTotal,
                createdAtEpochMillis = account.createdAtEpochMillis,
                updatedAtEpochMillis = account.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun insertCategory(profileId: Long, category: BackupCategory): Long {
        return categoryDao.insert(
            CategoryEntity(
                profileId = profileId,
                name = category.name,
                icon = category.icon,
                type = category.type.storageValue,
                color = category.color,
                isProtected = category.isProtected,
                updatedAtEpochMillis = category.updatedAtEpochMillis,
                deletedAtEpochMillis = category.deletedAtEpochMillis,
            ),
        )
    }

    override suspend fun insertTransaction(
        profileId: Long,
        transaction: BackupTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        return transactionDao.insert(
            TransactionEntity(
                profileId = profileId,
                type = transaction.type.storageValue,
                amountCents = transaction.amountCents,
                categoryId = categoryId,
                accountId = accountId,
                note = transaction.note,
                currencyCode = transaction.currencyCode,
                snapshotDate = transaction.snapshotDate,
                isAdjustment = transaction.isAdjustment,
                createdAtEpochMillis = transaction.createdAtEpochMillis,
            ),
        )
    }

    override suspend fun insertTransfer(
        profileId: Long,
        transfer: BackupTransfer,
        fromAccountId: Long,
        toAccountId: Long,
        fromTransactionId: Long?,
        toTransactionId: Long?,
    ): Long {
        return transferDao.insert(
            TransferEntity(
                profileId = profileId,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amountCents = transfer.amountCents,
                fromCurrencyCode = transfer.fromCurrencyCode,
                toCurrencyCode = transfer.toCurrencyCode,
                exchangeRateE8 = transfer.exchangeRateE8,
                note = transfer.note,
                fromTransactionId = fromTransactionId,
                toTransactionId = toTransactionId,
                createdAtEpochMillis = transfer.createdAtEpochMillis,
            ),
        )
    }

    override suspend fun insertBudget(profileId: Long, budget: BackupBudget, categoryId: Long): Long {
        return budgetDao.insert(
            BudgetEntity(
                profileId = profileId,
                categoryId = categoryId,
                limitCents = budget.limitCents,
                period = budget.period.storageValue,
                currencyCode = budget.currencyCode,
                notifyAtPercent = budget.notifyAtPercent,
                notificationsEnabled = budget.notificationsEnabled,
                lastNotifiedPercent = budget.lastNotifiedPercent,
                lastNotifiedAtEpochMillis = budget.lastNotifiedAtEpochMillis,
                createdAtEpochMillis = budget.createdAtEpochMillis,
                updatedAtEpochMillis = budget.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun insertRecurringTransaction(
        profileId: Long,
        recurring: BackupRecurringTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        return recurringTransactionDao.insert(
            RecurringTransactionEntity(
                profileId = profileId,
                accountId = accountId,
                categoryId = categoryId,
                type = recurring.type.storageValue,
                amountCents = recurring.amountCents,
                currencyCode = recurring.currencyCode,
                note = recurring.note,
                frequency = recurring.frequency.storageValue,
                nextRunAtEpochMillis = recurring.nextRunAtEpochMillis,
                isActive = recurring.isActive,
                createdAtEpochMillis = recurring.createdAtEpochMillis,
                updatedAtEpochMillis = recurring.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun insertSavingsGoal(profileId: Long, goal: BackupSavingsGoal, accountId: Long?): Long {
        return savingsGoalDao.insert(
            SavingsGoalEntity(
                profileId = profileId,
                name = goal.name,
                targetCents = goal.targetCents,
                currentCents = goal.currentCents,
                currencyCode = goal.currencyCode,
                deadlineDate = goal.deadlineDate,
                accountId = accountId,
                createdAtEpochMillis = goal.createdAtEpochMillis,
                updatedAtEpochMillis = goal.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun insertGoalTransaction(
        profileId: Long,
        transaction: BackupGoalTransaction,
        goalId: Long,
    ): Long {
        return goalTransactionDao.insert(
            GoalTransactionEntity(
                profileId = profileId,
                goalId = goalId,
                type = transaction.type.storageValue,
                amountCents = transaction.amountCents,
                createdAtEpochMillis = transaction.createdAtEpochMillis,
            ),
        )
    }

    override suspend fun saveExchangeRateSnapshot(snapshot: BackupExchangeRateSnapshot): Long {
        return exchangeRateSnapshotDao.upsert(
            ExchangeRateSnapshotEntity(
                snapshotDate = snapshot.snapshotDate,
                baseCurrency = snapshot.baseCurrencyCode,
                targetCurrency = snapshot.targetCurrencyCode,
                rateE8 = snapshot.rateE8,
                createdAtEpochMillis = snapshot.createdAtEpochMillis,
            ),
        )
    }

    override suspend fun saveExchangeRateOverride(profileId: Long, override: BackupExchangeRateOverride): Long {
        return exchangeRateOverrideDao.upsert(
            ExchangeRateOverrideEntity(
                profileId = profileId,
                effectiveDate = override.effectiveDate,
                baseCurrency = override.baseCurrencyCode,
                targetCurrency = override.targetCurrencyCode,
                rateE8 = override.rateE8,
                createdAtEpochMillis = override.createdAtEpochMillis,
                updatedAtEpochMillis = override.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun insertTransactionTemplate(
        profileId: Long,
        template: BackupTransactionTemplate,
        accountId: Long,
        categoryId: Long,
    ): Long {
        return transactionTemplateDao.insert(
            TransactionTemplateEntity(
                profileId = profileId,
                name = template.name,
                type = template.type.storageValue,
                amountCents = template.amountCents,
                amountFixed = template.amountFixed,
                categoryId = categoryId,
                accountId = accountId,
                currencyCode = template.currencyCode,
                note = template.note,
                sortOrder = template.sortOrder,
                createdAtEpochMillis = template.createdAtEpochMillis,
                updatedAtEpochMillis = template.updatedAtEpochMillis,
            ),
        )
    }
}

private fun MoneyTrackerBackup.selectedProfiles(
    options: MoneyTrackerBackupImportOptions,
): List<BackupProfile> {
    val selectedRefs = options.selectedProfileRefs ?: return profiles
    val availableRefs = profiles.mapTo(linkedSetOf()) { it.ref }
    val missingRefs = selectedRefs
        .filterNot { it in availableRefs }
        .sorted()
    if (missingRefs.isNotEmpty()) {
        throw MoneyTrackerBackupImportSelectionException(
            "Selected backup profiles were not found: ${missingRefs.joinToString()}",
        )
    }
    return profiles.filter { it.ref in selectedRefs }
}

private fun Map<String, Long>.requireLocalId(ref: String): Long {
    return this[ref] ?: throw MoneyTrackerBackupImportException(
        "Backup reference was validated but not mapped during import.",
    )
}

private fun List<String>.toCurrencyCsv(): String {
    return map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(separator = ",")
}

private val BackupAccountType.storageValue: String
    get() = when (this) {
        BackupAccountType.Checking -> "checking"
        BackupAccountType.Savings -> "savings"
        BackupAccountType.Cash -> "cash"
        BackupAccountType.Credit -> "credit"
        BackupAccountType.Crypto -> "crypto"
    }

private val BackupCategoryType.storageValue: String
    get() = when (this) {
        BackupCategoryType.Expense -> "expense"
        BackupCategoryType.Income -> "income"
        BackupCategoryType.Both -> "both"
        BackupCategoryType.Savings -> "savings"
        BackupCategoryType.Transfer -> "transfer"
        BackupCategoryType.Adjustment -> "adjustment"
    }

private val BackupTransactionType.storageValue: String
    get() = when (this) {
        BackupTransactionType.Expense -> "expense"
        BackupTransactionType.Income -> "income"
    }

private val BackupBudgetPeriod.storageValue: String
    get() = when (this) {
        BackupBudgetPeriod.Weekly -> "weekly"
        BackupBudgetPeriod.Monthly -> "monthly"
    }

private val BackupRecurringFrequency.storageValue: String
    get() = when (this) {
        BackupRecurringFrequency.Daily -> "daily"
        BackupRecurringFrequency.Weekly -> "weekly"
        BackupRecurringFrequency.Monthly -> "monthly"
        BackupRecurringFrequency.Yearly -> "yearly"
    }

private val BackupGoalTransactionType.storageValue: String
    get() = when (this) {
        BackupGoalTransactionType.Deposit -> "deposit"
        BackupGoalTransactionType.Withdraw -> "withdraw"
    }

private val BackupStatsChartStyle.storageValue: String
    get() = when (this) {
        BackupStatsChartStyle.Donut -> "donut"
        BackupStatsChartStyle.StackedBar -> "stacked_bar"
        BackupStatsChartStyle.DualBar -> "dual_bar"
        BackupStatsChartStyle.ProfitBars -> "profit_bars"
    }

private val BackupTheme.storageValue: String
    get() = when (this) {
        BackupTheme.System -> "system"
        BackupTheme.Light -> "light"
        BackupTheme.Dark -> "dark"
    }
