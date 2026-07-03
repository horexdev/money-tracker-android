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
import java.util.Locale

class MoneyTrackerBackupExporter internal constructor(
    private val store: MoneyTrackerBackupExportStore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(
        database: MoneyTrackerDatabase,
        currentTimeMillis: () -> Long = System::currentTimeMillis,
    ) : this(
        store = RoomMoneyTrackerBackupExportStore(database),
        currentTimeMillis = currentTimeMillis,
    )

    suspend fun exportBackup(
        options: MoneyTrackerBackupExportOptions = MoneyTrackerBackupExportOptions(),
    ): MoneyTrackerBackup {
        val backup = store.withTransaction {
            val profiles = listProfiles().selectedProfiles(options)
            val profileRefs = profiles.allocateRefs(prefix = "profile") { it.id }
            MoneyTrackerBackup(
                createdAtEpochMillis = currentTimeMillis(),
                profiles = profiles.map { profile ->
                    exportProfile(
                        profile = profile,
                        profileRef = profileRefs.requireRef(profile.id, "profile"),
                    )
                },
                exchangeRateSnapshots = listExchangeRateSnapshots().map { it.toBackup() },
            )
        }
        MoneyTrackerBackupV1Contract.requireValidExportRefs(backup)
        return backup
    }

    suspend fun exportBackupJson(
        options: MoneyTrackerBackupExportOptions = MoneyTrackerBackupExportOptions(),
    ): String {
        return MoneyTrackerBackupV1Json.encodeToString(exportBackup(options))
    }

    private suspend fun MoneyTrackerBackupExportStore.exportProfile(
        profile: LocalProfileEntity,
        profileRef: String,
    ): BackupProfile {
        val accounts = listAccounts(profile.id)
        val categories = listCategories(profile.id)
        val transactions = listTransactions(profile.id)
        val transfers = listTransfers(profile.id)
        val budgets = listBudgets(profile.id)
        val recurringTransactions = listRecurringTransactions(profile.id)
        val savingsGoals = listSavingsGoals(profile.id)
        val goalTransactions = listGoalTransactions(profile.id)
        val exchangeRateOverrides = listExchangeRateOverrides(profile.id)
        val transactionTemplates = listTransactionTemplates(profile.id)

        val accountRefs = accounts.allocateRefs(prefix = "account") { it.id }
        val categoryRefs = categories.allocateRefs(prefix = "category") { it.id }
        val transactionRefs = transactions.allocateRefs(prefix = "transaction") { it.id }
        val transferRefs = transfers.allocateRefs(prefix = "transfer") { it.id }
        val budgetRefs = budgets.allocateRefs(prefix = "budget") { it.id }
        val recurringRefs = recurringTransactions.allocateRefs(prefix = "recurring") { it.id }
        val savingsGoalRefs = savingsGoals.allocateRefs(prefix = "goal") { it.id }
        val goalTransactionRefs = goalTransactions.allocateRefs(prefix = "goal-transaction") { it.id }
        val exchangeRateOverrideRefs = exchangeRateOverrides.allocateRefs(prefix = "rate-override") { it.id }
        val transactionTemplateRefs = transactionTemplates.allocateRefs(prefix = "template") { it.id }

        return BackupProfile(
            ref = profileRef,
            label = profile.label,
            languageCode = profile.languageCode,
            displayCurrencyCodes = profile.displayCurrenciesCsv.toDisplayCurrencyCodes(),
            notificationPreferences = BackupNotificationPreferences(
                notifyBudgetAlerts = profile.notifyBudgetAlerts,
                notifyRecurringReminders = profile.notifyRecurringReminders,
                notifyWeeklySummary = profile.notifyWeeklySummary,
                notifyGoalMilestones = profile.notifyGoalMilestones,
            ),
            uiPreferences = BackupUiPreferences(
                statsChartStyle = profile.statsChartStyle.toBackupStatsChartStyle(),
                animateNumbers = profile.animateNumbers,
                theme = profile.theme.toBackupTheme(),
                hideAmounts = profile.hideAmounts,
            ),
            createdAtEpochMillis = profile.createdAtEpochMillis,
            updatedAtEpochMillis = profile.updatedAtEpochMillis,
            accounts = accounts.map { account ->
                account.toBackup(accountRefs.requireRef(account.id, "account"))
            },
            categories = categories.map { category ->
                category.toBackup(categoryRefs.requireRef(category.id, "category"))
            },
            transactions = transactions.map { transaction ->
                transaction.toBackup(
                    ref = transactionRefs.requireRef(transaction.id, "transaction"),
                    accountRefs = accountRefs,
                    categoryRefs = categoryRefs,
                )
            },
            transfers = transfers.map { transfer ->
                transfer.toBackup(
                    ref = transferRefs.requireRef(transfer.id, "transfer"),
                    accountRefs = accountRefs,
                    transactionRefs = transactionRefs,
                )
            },
            budgets = budgets.map { budget ->
                budget.toBackup(
                    ref = budgetRefs.requireRef(budget.id, "budget"),
                    categoryRefs = categoryRefs,
                )
            },
            recurringTransactions = recurringTransactions.map { recurring ->
                recurring.toBackup(
                    ref = recurringRefs.requireRef(recurring.id, "recurring transaction"),
                    accountRefs = accountRefs,
                    categoryRefs = categoryRefs,
                )
            },
            savingsGoals = savingsGoals.map { goal ->
                goal.toBackup(
                    ref = savingsGoalRefs.requireRef(goal.id, "savings goal"),
                    accountRefs = accountRefs,
                )
            },
            goalTransactions = goalTransactions.map { transaction ->
                transaction.toBackup(
                    ref = goalTransactionRefs.requireRef(transaction.id, "goal transaction"),
                    savingsGoalRefs = savingsGoalRefs,
                )
            },
            exchangeRateOverrides = exchangeRateOverrides.map { override ->
                override.toBackup(exchangeRateOverrideRefs.requireRef(override.id, "exchange rate override"))
            },
            transactionTemplates = transactionTemplates.map { template ->
                template.toBackup(
                    ref = transactionTemplateRefs.requireRef(template.id, "transaction template"),
                    accountRefs = accountRefs,
                    categoryRefs = categoryRefs,
                )
            },
        )
    }
}

data class MoneyTrackerBackupExportOptions(
    val selectedLocalProfileIds: Set<Long>? = null,
)

open class MoneyTrackerBackupExportException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class MoneyTrackerBackupExportSelectionException(
    message: String,
) : MoneyTrackerBackupExportException(message)

internal interface MoneyTrackerBackupExportStore {
    suspend fun <T> withTransaction(block: suspend MoneyTrackerBackupExportStore.() -> T): T
    suspend fun listProfiles(): List<LocalProfileEntity>
    suspend fun listAccounts(profileId: Long): List<AccountEntity>
    suspend fun listCategories(profileId: Long): List<CategoryEntity>
    suspend fun listTransactions(profileId: Long): List<TransactionEntity>
    suspend fun listTransfers(profileId: Long): List<TransferEntity>
    suspend fun listBudgets(profileId: Long): List<BudgetEntity>
    suspend fun listRecurringTransactions(profileId: Long): List<RecurringTransactionEntity>
    suspend fun listSavingsGoals(profileId: Long): List<SavingsGoalEntity>
    suspend fun listGoalTransactions(profileId: Long): List<GoalTransactionEntity>
    suspend fun listExchangeRateSnapshots(): List<ExchangeRateSnapshotEntity>
    suspend fun listExchangeRateOverrides(profileId: Long): List<ExchangeRateOverrideEntity>
    suspend fun listTransactionTemplates(profileId: Long): List<TransactionTemplateEntity>
}

private class RoomMoneyTrackerBackupExportStore(
    private val database: MoneyTrackerDatabase,
) : MoneyTrackerBackupExportStore {
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
        block: suspend MoneyTrackerBackupExportStore.() -> T,
    ): T {
        return database.withTransaction { this@RoomMoneyTrackerBackupExportStore.block() }
    }

    override suspend fun listProfiles(): List<LocalProfileEntity> {
        return localProfileDao.listForBackup()
    }

    override suspend fun listAccounts(profileId: Long): List<AccountEntity> {
        return accountDao.listForBackup(profileId)
    }

    override suspend fun listCategories(profileId: Long): List<CategoryEntity> {
        return categoryDao.listForBackup(profileId)
    }

    override suspend fun listTransactions(profileId: Long): List<TransactionEntity> {
        return transactionDao.listForBackup(profileId)
    }

    override suspend fun listTransfers(profileId: Long): List<TransferEntity> {
        return transferDao.listForBackup(profileId)
    }

    override suspend fun listBudgets(profileId: Long): List<BudgetEntity> {
        return budgetDao.listForBackup(profileId)
    }

    override suspend fun listRecurringTransactions(profileId: Long): List<RecurringTransactionEntity> {
        return recurringTransactionDao.listForBackup(profileId)
    }

    override suspend fun listSavingsGoals(profileId: Long): List<SavingsGoalEntity> {
        return savingsGoalDao.listForBackup(profileId)
    }

    override suspend fun listGoalTransactions(profileId: Long): List<GoalTransactionEntity> {
        return goalTransactionDao.listForBackup(profileId)
    }

    override suspend fun listExchangeRateSnapshots(): List<ExchangeRateSnapshotEntity> {
        return exchangeRateSnapshotDao.listForBackup()
    }

    override suspend fun listExchangeRateOverrides(profileId: Long): List<ExchangeRateOverrideEntity> {
        return exchangeRateOverrideDao.listForBackup(profileId)
    }

    override suspend fun listTransactionTemplates(profileId: Long): List<TransactionTemplateEntity> {
        return transactionTemplateDao.listForBackup(profileId)
    }
}

private fun List<LocalProfileEntity>.selectedProfiles(
    options: MoneyTrackerBackupExportOptions,
): List<LocalProfileEntity> {
    val selectedIds = options.selectedLocalProfileIds ?: return this
    val availableIds = mapTo(linkedSetOf()) { it.id }
    val missingIds = selectedIds
        .filterNot { it in availableIds }
        .sorted()
    if (missingIds.isNotEmpty()) {
        throw MoneyTrackerBackupExportSelectionException(
            "Selected local profiles were not found: ${missingIds.joinToString()}",
        )
    }
    return filter { it.id in selectedIds }
}

private fun <T> List<T>.allocateRefs(prefix: String, idSelector: (T) -> Long): Map<Long, String> {
    return mapIndexed { index, item ->
        idSelector(item) to "$prefix:${index + 1}"
    }.toMap(linkedMapOf())
}

private fun Map<Long, String>.requireRef(localId: Long, label: String): String {
    return this[localId] ?: throw MoneyTrackerBackupExportException(
        "Missing export ref for $label local row.",
    )
}

private fun AccountEntity.toBackup(ref: String): BackupAccount {
    return BackupAccount(
        ref = ref,
        name = name,
        icon = icon,
        color = color,
        type = type.toBackupAccountType(),
        currencyCode = currencyCode,
        isDefault = isDefault,
        includeInTotal = includeInTotal,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun CategoryEntity.toBackup(ref: String): BackupCategory {
    return BackupCategory(
        ref = ref,
        name = name,
        localizationKey = localizationKey,
        icon = icon,
        type = type.toBackupCategoryType(),
        color = color,
        isProtected = isProtected,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis,
    )
}

private fun TransactionEntity.toBackup(
    ref: String,
    accountRefs: Map<Long, String>,
    categoryRefs: Map<Long, String>,
): BackupTransaction {
    return BackupTransaction(
        ref = ref,
        type = type.toBackupTransactionType(),
        amountCents = amountCents,
        categoryRef = categoryRefs.requireRef(categoryId, "transaction category"),
        accountRef = accountRefs.requireRef(accountId, "transaction account"),
        note = note,
        currencyCode = currencyCode,
        snapshotDate = snapshotDate,
        createdAtEpochMillis = createdAtEpochMillis,
        isAdjustment = isAdjustment,
    )
}

private fun TransferEntity.toBackup(
    ref: String,
    accountRefs: Map<Long, String>,
    transactionRefs: Map<Long, String>,
): BackupTransfer {
    return BackupTransfer(
        ref = ref,
        fromAccountRef = accountRefs.requireRef(fromAccountId, "transfer source account"),
        toAccountRef = accountRefs.requireRef(toAccountId, "transfer destination account"),
        amountCents = amountCents,
        fromCurrencyCode = fromCurrencyCode,
        toCurrencyCode = toCurrencyCode,
        exchangeRateE8 = exchangeRateE8,
        note = note,
        fromTransactionRef = fromTransactionId?.let { transactionRefs.requireRef(it, "transfer source transaction") },
        toTransactionRef = toTransactionId?.let { transactionRefs.requireRef(it, "transfer destination transaction") },
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

private fun BudgetEntity.toBackup(
    ref: String,
    categoryRefs: Map<Long, String>,
): BackupBudget {
    return BackupBudget(
        ref = ref,
        categoryRef = categoryRefs.requireRef(categoryId, "budget category"),
        limitCents = limitCents,
        period = period.toBackupBudgetPeriod(),
        currencyCode = currencyCode,
        notifyAtPercent = notifyAtPercent,
        notificationsEnabled = notificationsEnabled,
        lastNotifiedPercent = lastNotifiedPercent,
        lastNotifiedAtEpochMillis = lastNotifiedAtEpochMillis,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun RecurringTransactionEntity.toBackup(
    ref: String,
    accountRefs: Map<Long, String>,
    categoryRefs: Map<Long, String>,
): BackupRecurringTransaction {
    return BackupRecurringTransaction(
        ref = ref,
        accountRef = accountRefs.requireRef(accountId, "recurring transaction account"),
        categoryRef = categoryRefs.requireRef(categoryId, "recurring transaction category"),
        type = type.toBackupTransactionType(),
        amountCents = amountCents,
        currencyCode = currencyCode,
        note = note,
        frequency = frequency.toBackupRecurringFrequency(),
        nextRunAtEpochMillis = nextRunAtEpochMillis,
        isActive = isActive,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun SavingsGoalEntity.toBackup(
    ref: String,
    accountRefs: Map<Long, String>,
): BackupSavingsGoal {
    return BackupSavingsGoal(
        ref = ref,
        name = name,
        targetCents = targetCents,
        currentCents = currentCents,
        currencyCode = currencyCode,
        deadlineDate = deadlineDate,
        accountRef = accountId?.let { accountRefs.requireRef(it, "savings goal account") },
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun GoalTransactionEntity.toBackup(
    ref: String,
    savingsGoalRefs: Map<Long, String>,
): BackupGoalTransaction {
    return BackupGoalTransaction(
        ref = ref,
        goalRef = savingsGoalRefs.requireRef(goalId, "goal transaction goal"),
        type = type.toBackupGoalTransactionType(),
        amountCents = amountCents,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

private fun ExchangeRateSnapshotEntity.toBackup(): BackupExchangeRateSnapshot {
    return BackupExchangeRateSnapshot(
        snapshotDate = snapshotDate,
        baseCurrencyCode = baseCurrency,
        targetCurrencyCode = targetCurrency,
        rateE8 = rateE8,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

private fun ExchangeRateOverrideEntity.toBackup(ref: String): BackupExchangeRateOverride {
    return BackupExchangeRateOverride(
        ref = ref,
        effectiveDate = effectiveDate,
        baseCurrencyCode = baseCurrency,
        targetCurrencyCode = targetCurrency,
        rateE8 = rateE8,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun TransactionTemplateEntity.toBackup(
    ref: String,
    accountRefs: Map<Long, String>,
    categoryRefs: Map<Long, String>,
): BackupTransactionTemplate {
    return BackupTransactionTemplate(
        ref = ref,
        name = name,
        type = type.toBackupTransactionType(),
        amountCents = amountCents,
        amountFixed = amountFixed,
        categoryRef = categoryRefs.requireRef(categoryId, "transaction template category"),
        accountRef = accountRefs.requireRef(accountId, "transaction template account"),
        currencyCode = currencyCode,
        note = note,
        sortOrder = sortOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun String.toDisplayCurrencyCodes(): List<String> {
    return split(",")
        .map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotEmpty() }
        .distinct()
}

private fun String.toBackupAccountType(): BackupAccountType {
    return when (normalizedStorageValue()) {
        "checking" -> BackupAccountType.Checking
        "savings" -> BackupAccountType.Savings
        "cash" -> BackupAccountType.Cash
        "credit" -> BackupAccountType.Credit
        "crypto" -> BackupAccountType.Crypto
        else -> unsupportedStorageValue("account type", this)
    }
}

private fun String.toBackupCategoryType(): BackupCategoryType {
    return when (normalizedStorageValue()) {
        "expense" -> BackupCategoryType.Expense
        "income" -> BackupCategoryType.Income
        "both" -> BackupCategoryType.Both
        "savings" -> BackupCategoryType.Savings
        "transfer" -> BackupCategoryType.Transfer
        "adjustment" -> BackupCategoryType.Adjustment
        else -> unsupportedStorageValue("category type", this)
    }
}

private fun String.toBackupTransactionType(): BackupTransactionType {
    return when (normalizedStorageValue()) {
        "expense" -> BackupTransactionType.Expense
        "income" -> BackupTransactionType.Income
        else -> unsupportedStorageValue("transaction type", this)
    }
}

private fun String.toBackupBudgetPeriod(): BackupBudgetPeriod {
    return when (normalizedStorageValue()) {
        "weekly" -> BackupBudgetPeriod.Weekly
        "monthly" -> BackupBudgetPeriod.Monthly
        else -> unsupportedStorageValue("budget period", this)
    }
}

private fun String.toBackupRecurringFrequency(): BackupRecurringFrequency {
    return when (normalizedStorageValue()) {
        "daily" -> BackupRecurringFrequency.Daily
        "weekly" -> BackupRecurringFrequency.Weekly
        "monthly" -> BackupRecurringFrequency.Monthly
        "yearly" -> BackupRecurringFrequency.Yearly
        else -> unsupportedStorageValue("recurring frequency", this)
    }
}

private fun String.toBackupGoalTransactionType(): BackupGoalTransactionType {
    return when (normalizedStorageValue()) {
        "deposit" -> BackupGoalTransactionType.Deposit
        "withdraw" -> BackupGoalTransactionType.Withdraw
        else -> unsupportedStorageValue("goal transaction type", this)
    }
}

private fun String.toBackupStatsChartStyle(): BackupStatsChartStyle {
    return when (normalizedStorageValue()) {
        "donut" -> BackupStatsChartStyle.Donut
        "stacked_bar" -> BackupStatsChartStyle.StackedBar
        "dual_bar" -> BackupStatsChartStyle.DualBar
        "profit_bars" -> BackupStatsChartStyle.ProfitBars
        else -> unsupportedStorageValue("stats chart style", this)
    }
}

private fun String.toBackupTheme(): BackupTheme {
    return when (normalizedStorageValue()) {
        "system" -> BackupTheme.System
        "light" -> BackupTheme.Light
        "dark" -> BackupTheme.Dark
        else -> unsupportedStorageValue("theme", this)
    }
}

private fun String.normalizedStorageValue(): String {
    return trim().lowercase(Locale.US)
}

private fun unsupportedStorageValue(kind: String, value: String): Nothing {
    throw MoneyTrackerBackupExportException(
        "Unsupported $kind storage value '$value'.",
    )
}
