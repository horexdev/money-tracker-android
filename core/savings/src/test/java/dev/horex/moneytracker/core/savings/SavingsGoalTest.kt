package dev.horex.moneytracker.core.savings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsGoalTest {
    @Test
    fun derivedProgressValuesAreCappedAndNonNegative() {
        val goal = SavingsGoal(
            id = 1,
            profileId = 2,
            name = "Trip",
            targetCents = 10_000,
            currentCents = 12_500,
            currencyCode = "USD",
            deadlineDate = null,
            accountId = null,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )

        assertEquals(100.0, goal.progressPercent, 0.0)
        assertTrue(goal.isCompleted)
        assertEquals(0L, goal.remainingCents)
        assertEquals(0.0, savingsGoalProgressPercent(1_000, 0), 0.0)
    }

    @Test
    fun historyCurrentUsesDepositMinusWithdraw() {
        val history = listOf(
            historyEntry(SavingsGoalTransactionType.Deposit, 5_000),
            historyEntry(SavingsGoalTransactionType.Deposit, 2_500),
            historyEntry(SavingsGoalTransactionType.Withdraw, 1_000),
        )

        assertEquals(6_500L, savingsGoalHistoryCurrentCents(history))
    }

    @Test
    fun historyTypeParsingRejectsUnknownValues() {
        assertEquals(SavingsGoalTransactionType.Deposit, SavingsGoalTransactionType.fromStorageValue("deposit"))
        assertEquals(SavingsGoalTransactionType.Withdraw, SavingsGoalTransactionType.fromStorageValue("withdraw"))
        assertThrows(InvalidSavingsGoalHistoryTypeException::class.java) {
            SavingsGoalTransactionType.fromStorageValue("move")
        }
    }

    @Test
    fun incompleteGoalReportsRemainingAmount() {
        val goal = SavingsGoal(
            id = 1,
            profileId = 2,
            name = "Trip",
            targetCents = 10_000,
            currentCents = 4_000,
            currencyCode = "USD",
            deadlineDate = "2026-12-31",
            accountId = 3,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )

        assertEquals(40.0, goal.progressPercent, 0.0)
        assertFalse(goal.isCompleted)
        assertEquals(6_000L, goal.remainingCents)
    }

    private fun historyEntry(type: SavingsGoalTransactionType, amountCents: Long): SavingsGoalHistoryEntry {
        return SavingsGoalHistoryEntry(
            id = 0,
            profileId = 1,
            goalId = 1,
            type = type,
            amountCents = amountCents,
            createdAtEpochMillis = 1,
        )
    }
}
