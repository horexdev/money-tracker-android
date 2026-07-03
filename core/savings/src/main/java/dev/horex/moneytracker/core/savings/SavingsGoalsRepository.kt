package dev.horex.moneytracker.core.savings

interface SavingsGoalsRepository {
    suspend fun listGoals(profileId: Long): List<SavingsGoal>

    suspend fun getGoal(profileId: Long, goalId: Long): SavingsGoal

    suspend fun createGoal(profileId: Long, input: CreateSavingsGoalInput): SavingsGoal

    suspend fun updateGoal(profileId: Long, goalId: Long, input: UpdateSavingsGoalInput): SavingsGoal

    suspend fun deleteGoal(profileId: Long, goalId: Long)

    suspend fun deposit(profileId: Long, goalId: Long, input: SavingsGoalOperationInput): SavingsGoal

    suspend fun withdraw(profileId: Long, goalId: Long, input: SavingsGoalOperationInput): SavingsGoal

    suspend fun listHistory(profileId: Long, goalId: Long): List<SavingsGoalHistoryEntry>
}
