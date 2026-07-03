package dev.horex.moneytracker.core.savings

data class SavingsGoal(
    val id: Long,
    val profileId: Long,
    val name: String,
    val targetCents: Long,
    val currentCents: Long,
    val currencyCode: String,
    val deadlineDate: String?,
    val accountId: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val progressPercent: Double
        get() = savingsGoalProgressPercent(currentCents, targetCents)

    val isCompleted: Boolean
        get() = currentCents >= targetCents

    val remainingCents: Long
        get() = (targetCents - currentCents).coerceAtLeast(0L)

    fun crossedMilestones(previousCurrentCents: Long): List<Int> {
        val previousProgress = savingsGoalProgressPercent(previousCurrentCents, targetCents)
        return SavingsGoalMilestones.filter { milestone ->
            previousProgress < milestone && progressPercent >= milestone
        }
    }
}

val SavingsGoalMilestones = listOf(25, 50, 75, 100)

data class SavingsGoalHistoryEntry(
    val id: Long,
    val profileId: Long,
    val goalId: Long,
    val type: SavingsGoalTransactionType,
    val amountCents: Long,
    val createdAtEpochMillis: Long,
)

enum class SavingsGoalTransactionType(val storageValue: String) {
    Deposit("deposit"),
    Withdraw("withdraw"),
    ;

    companion object {
        fun fromStorageValue(value: String): SavingsGoalTransactionType {
            return entries.firstOrNull { it.storageValue == value } ?: throw InvalidSavingsGoalHistoryTypeException()
        }
    }
}

data class CreateSavingsGoalInput(
    val name: String,
    val targetCents: Long,
    val currencyCode: String,
    val deadlineDate: String? = null,
    val accountId: Long? = null,
    val createdAtEpochMillis: Long? = null,
)

data class UpdateSavingsGoalInput(
    val name: String? = null,
    val targetCents: Long? = null,
    val currencyCode: String? = null,
    val deadlineDate: String? = null,
    val clearDeadline: Boolean = false,
    val accountId: Long? = null,
    val clearLinkedAccount: Boolean = false,
)

data class SavingsGoalOperationInput(
    val amountCents: Long,
    val note: String? = null,
    val createdAtEpochMillis: Long? = null,
)

internal fun savingsGoalProgressPercent(currentCents: Long, targetCents: Long): Double {
    if (targetCents <= 0L) {
        return 0.0
    }
    return (currentCents.toDouble() / targetCents.toDouble() * 100.0).coerceIn(0.0, 100.0)
}

internal fun savingsGoalHistoryCurrentCents(history: List<SavingsGoalHistoryEntry>): Long {
    return history.fold(0L) { current, entry ->
        when (entry.type) {
            SavingsGoalTransactionType.Deposit -> current + entry.amountCents
            SavingsGoalTransactionType.Withdraw -> current - entry.amountCents
        }
    }
}

internal fun savingsGoalHistoryMaxCurrentCents(history: List<SavingsGoalHistoryEntry>): Long {
    var current = 0L
    var maximum = 0L
    history.forEach { entry ->
        current = when (entry.type) {
            SavingsGoalTransactionType.Deposit -> current + entry.amountCents
            SavingsGoalTransactionType.Withdraw -> current - entry.amountCents
        }
        maximum = maxOf(maximum, current)
    }
    return maximum
}
