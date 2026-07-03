package dev.horex.moneytracker.core.budgets

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.dao.BudgetTransactionWithRelations
import dev.horex.moneytracker.core.database.dao.BudgetWithCategory
import dev.horex.moneytracker.core.database.model.BudgetEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.SystemCategoryLocalization
import java.time.ZoneId
import java.util.Locale

class RoomBudgetsRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : BudgetsRepository {
    private val budgetDao = database.budgetDao()
    private val categoryDao = database.categoryDao()
    private val localProfileDao = database.localProfileDao()

    override suspend fun listBudgets(profileId: Long): List<Budget> {
        val languageCode = requireProfileLanguage(profileId)
        return budgetDao.listWithCategoryByProfile(profileId).map { row ->
            row.toBudget(spentCents = row.budget.spentCentsForCurrentPeriod(), languageCode = languageCode)
        }
    }

    override suspend fun getBudget(profileId: Long, budgetId: Long): Budget {
        val languageCode = requireProfileLanguage(profileId)
        val row = budgetDao.getWithCategoryById(profileId, budgetId) ?: throw BudgetNotFoundException()
        return row.toBudget(spentCents = row.budget.spentCentsForCurrentPeriod(), languageCode = languageCode)
    }

    override suspend fun createBudget(profileId: Long, input: CreateBudgetInput): Budget {
        val normalized = input.normalized()
        val now = clock()
        val budgetId = database.withTransaction {
            val category = requireBudgetCategory(profileId, normalized.categoryId)
            requireNoDuplicateBudget(profileId, category.id, normalized.period, exceptBudgetId = null)
            budgetDao.insert(
                BudgetEntity(
                    profileId = profileId,
                    categoryId = category.id,
                    limitCents = normalized.limitCents,
                    period = normalized.period.storageValue,
                    currencyCode = normalized.currencyCode,
                    notifyAtPercent = normalized.notifyAtPercent,
                    notificationsEnabled = normalized.notificationsEnabled,
                    lastNotifiedPercent = 0,
                    lastNotifiedAtEpochMillis = null,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }
        return getBudget(profileId, budgetId)
    }

    override suspend fun updateBudget(
        profileId: Long,
        budgetId: Long,
        input: UpdateBudgetInput,
    ): Budget {
        val now = clock()
        database.withTransaction {
            val existing = budgetDao.getById(profileId, budgetId) ?: throw BudgetNotFoundException()
            val limitCents = input.limitCents ?: existing.limitCents
            requirePositiveAmount(limitCents)
            val notifyAtPercent = input.notifyAtPercent ?: existing.notifyAtPercent
            requirePositiveNotifyPercent(notifyAtPercent)
            val period = input.period ?: BudgetPeriod.fromStorageValue(existing.period)

            requireBudgetCategory(profileId, existing.categoryId)
            requireNoDuplicateBudget(profileId, existing.categoryId, period, exceptBudgetId = existing.id)

            val updated = existing.copy(
                limitCents = limitCents,
                period = period.storageValue,
                notifyAtPercent = notifyAtPercent,
                notificationsEnabled = input.notificationsEnabled ?: existing.notificationsEnabled,
                updatedAtEpochMillis = now,
            )
            if (budgetDao.updateAndReturnCount(updated) != 1) {
                throw BudgetNotFoundException()
            }
        }
        return getBudget(profileId, budgetId)
    }

    override suspend fun deleteBudget(profileId: Long, budgetId: Long) {
        if (budgetDao.deleteById(profileId, budgetId) != 1) {
            throw BudgetNotFoundException()
        }
    }

    override suspend fun listBudgetTransactions(profileId: Long, budgetId: Long): List<BudgetTransaction> {
        val languageCode = requireProfileLanguage(profileId)
        val budget = budgetDao.getById(profileId, budgetId) ?: throw BudgetNotFoundException()
        val range = BudgetPeriod.fromStorageValue(budget.period).currentRange(clock(), zoneId)
        return budgetDao.listTransactionsInPeriod(
            profileId = profileId,
            categoryId = budget.categoryId,
            fromEpochMillisInclusive = range.fromEpochMillisInclusive,
            toEpochMillisExclusive = range.toEpochMillisExclusive,
        ).map { it.toBudgetTransaction(languageCode) }
    }

    override suspend fun recordBudgetThresholdNotification(
        profileId: Long,
        budgetId: Long,
        thresholdPercent: Int,
        periodStartEpochMillis: Long,
        notifiedAtEpochMillis: Long,
    ): Boolean {
        requireBudgetAlertThreshold(thresholdPercent)
        require(periodStartEpochMillis >= 0L) { "Budget period start must not be negative" }
        require(notifiedAtEpochMillis >= 0L) { "Budget notification time must not be negative" }

        return budgetDao.recordThresholdNotification(
            profileId = profileId,
            budgetId = budgetId,
            thresholdPercent = thresholdPercent,
            periodStartEpochMillis = periodStartEpochMillis,
            notifiedAtEpochMillis = notifiedAtEpochMillis,
        ) == 1
    }

    private suspend fun requireBudgetCategory(profileId: Long, categoryId: Long): CategoryEntity {
        val category = categoryDao.getActiveById(profileId, categoryId) ?: throw BudgetCategoryNotFoundException()
        if (category.type != EXPENSE_CATEGORY_TYPE && category.type != BOTH_CATEGORY_TYPE) {
            throw BudgetCategoryTypeException()
        }
        return category
    }

    private suspend fun requireNoDuplicateBudget(
        profileId: Long,
        categoryId: Long,
        period: BudgetPeriod,
        exceptBudgetId: Long?,
    ) {
        val duplicate = budgetDao.getByCategoryPeriod(profileId, categoryId, period.storageValue)
        if (duplicate != null && duplicate.id != exceptBudgetId) {
            throw BudgetAlreadyExistsException()
        }
    }

    private suspend fun BudgetEntity.spentCentsForCurrentPeriod(): Long {
        val range = BudgetPeriod.fromStorageValue(period).currentRange(clock(), zoneId)
        return budgetDao.getSpentInPeriod(
            profileId = profileId,
            categoryId = categoryId,
            currencyCode = currencyCode,
            fromEpochMillisInclusive = range.fromEpochMillisInclusive,
            toEpochMillisExclusive = range.toEpochMillisExclusive,
        )
    }

    private suspend fun requireProfileLanguage(profileId: Long): String {
        return localProfileDao.getById(profileId)?.languageCode ?: throw BudgetNotFoundException()
    }
}

private const val EXPENSE_CATEGORY_TYPE = "expense"
private const val BOTH_CATEGORY_TYPE = "both"
private val CurrencyCodePattern = Regex("[A-Z]{3}")

private fun CreateBudgetInput.normalized(): CreateBudgetInput {
    requirePositiveAmount(limitCents)
    requirePositiveNotifyPercent(notifyAtPercent)
    return copy(currencyCode = currencyCode.normalizedCurrencyCode())
}

private fun requirePositiveAmount(amountCents: Long) {
    if (amountCents <= 0) {
        throw InvalidBudgetAmountException()
    }
}

private fun requirePositiveNotifyPercent(percent: Int) {
    if (percent <= 0) {
        throw InvalidBudgetNotifyPercentException()
    }
}

private fun requireBudgetAlertThreshold(percent: Int) {
    if (percent !in BudgetAlertThresholds) {
        throw InvalidBudgetNotifyPercentException()
    }
}

private fun String.normalizedCurrencyCode(): String {
    return trim()
        .uppercase(Locale.US)
        .takeIf { CurrencyCodePattern.matches(it) }
        ?: throw InvalidBudgetCurrencyException()
}

private fun BudgetWithCategory.toBudget(spentCents: Long, languageCode: String): Budget {
    return Budget(
        id = budget.id,
        profileId = budget.profileId,
        categoryId = budget.categoryId,
        categoryName = SystemCategoryLocalization.displayName(categoryLocalizationKey, languageCode, categoryName),
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        limitCents = budget.limitCents,
        spentCents = spentCents,
        period = BudgetPeriod.fromStorageValue(budget.period),
        currencyCode = budget.currencyCode,
        notifyAtPercent = budget.notifyAtPercent,
        notificationsEnabled = budget.notificationsEnabled,
        lastNotifiedPercent = budget.lastNotifiedPercent,
        lastNotifiedAtEpochMillis = budget.lastNotifiedAtEpochMillis,
        createdAtEpochMillis = budget.createdAtEpochMillis,
        updatedAtEpochMillis = budget.updatedAtEpochMillis,
    )
}

private fun BudgetTransactionWithRelations.toBudgetTransaction(languageCode: String): BudgetTransaction {
    return BudgetTransaction(
        id = transaction.id,
        profileId = transaction.profileId,
        amountCents = transaction.amountCents,
        categoryId = transaction.categoryId,
        categoryName = SystemCategoryLocalization.displayName(categoryLocalizationKey, languageCode, categoryName),
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        accountId = transaction.accountId,
        accountName = accountName,
        note = transaction.note,
        currencyCode = transaction.currencyCode,
        snapshotDate = transaction.snapshotDate,
        createdAtEpochMillis = transaction.createdAtEpochMillis,
    )
}
