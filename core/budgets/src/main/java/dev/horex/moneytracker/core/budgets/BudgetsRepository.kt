package dev.horex.moneytracker.core.budgets

interface BudgetsRepository {
    suspend fun listBudgets(profileId: Long): List<Budget>

    suspend fun getBudget(profileId: Long, budgetId: Long): Budget

    suspend fun createBudget(profileId: Long, input: CreateBudgetInput): Budget

    suspend fun updateBudget(profileId: Long, budgetId: Long, input: UpdateBudgetInput): Budget

    suspend fun deleteBudget(profileId: Long, budgetId: Long)

    suspend fun listBudgetTransactions(profileId: Long, budgetId: Long): List<BudgetTransaction>

    suspend fun recordBudgetThresholdNotification(
        profileId: Long,
        budgetId: Long,
        thresholdPercent: Int,
        periodStartEpochMillis: Long,
        notifiedAtEpochMillis: Long,
    ): Boolean
}
