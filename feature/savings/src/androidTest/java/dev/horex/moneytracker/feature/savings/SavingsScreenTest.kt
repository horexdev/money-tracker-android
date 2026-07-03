package dev.horex.moneytracker.feature.savings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.accounts.Account
import dev.horex.moneytracker.core.accounts.AccountType
import dev.horex.moneytracker.core.accounts.AccountsRepository
import dev.horex.moneytracker.core.accounts.CreateAccountInput
import dev.horex.moneytracker.core.accounts.UpdateAccountInput
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.savings.CreateSavingsGoalInput
import dev.horex.moneytracker.core.savings.SavingsGoal
import dev.horex.moneytracker.core.savings.SavingsGoalHistoryEntry
import dev.horex.moneytracker.core.savings.SavingsGoalOperationInput
import dev.horex.moneytracker.core.savings.SavingsGoalTransactionType
import dev.horex.moneytracker.core.savings.SavingsGoalsRepository
import dev.horex.moneytracker.core.savings.UpdateSavingsGoalInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savingsScreenRendersLongGoalNameProgressAndCompletedStateOnSmallWidth() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 760.dp)) {
                    SavingsScreen(
                        state = SavingsUiState(
                            goals = listOf(
                                goalFixture(
                                    id = 1,
                                    name = "Emergency fund with a very long title that should stay readable on narrow phones",
                                    currentCents = 75_000,
                                    targetCents = 100_000,
                                ),
                                goalFixture(
                                    id = 2,
                                    name = "Laptop",
                                    currentCents = 150_000,
                                    targetCents = 150_000,
                                ),
                            ),
                            accounts = accountsFixture,
                        ),
                        onRetry = {},
                        onAddGoal = {},
                        onEditGoal = {},
                        onDeleteGoal = {},
                        onDeposit = {},
                        onWithdraw = {},
                        onOpenHistory = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Savings").assertIsDisplayed()
        composeRule.onNodeWithText("75% complete").assertIsDisplayed()
        composeRule.onNodeWithTag("savings-list")
            .performScrollToNode(hasTestTag("savings-goal-2"))
        composeRule.onNodeWithText("Laptop").assertIsDisplayed()
        composeRule.onNodeWithText("Completed").assertIsDisplayed()
        composeRule.onNodeWithText("100% complete").assertIsDisplayed()
    }

    @Test
    fun savingsRouteCreatesGoalAndRunsDepositWithdrawHistory() {
        val repository = FakeSavingsGoalsRepository(
            initialGoals = listOf(goalFixture(id = 1, name = "Emergency fund")),
            initialHistory = listOf(
                historyFixture(
                    id = 1,
                    type = SavingsGoalTransactionType.Deposit,
                    amountCents = 10_000,
                ),
            ),
        )

        composeRule.setContent {
            MoneyTrackerTheme {
                SavingsRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    savingsGoalsRepository = repository,
                    accountsRepository = FakeAccountsRepository(),
                )
            }
        }

        composeRule.onNodeWithText("Emergency fund").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add goal").performClick()
        composeRule.onNodeWithTag("savings-name").performTextReplacement("Vacation")
        composeRule.onNodeWithTag("savings-target").performTextReplacement("1200.00")
        composeRule.onNodeWithTag("savings-currency").performClick()
        composeRule.onNodeWithTag("savings-currency-search").performTextReplacement("Euro")
        composeRule.onNodeWithTag("savings-currency-option-EUR").performClick()
        composeRule.onNodeWithTag("savings-deadline").performTextReplacement("2026-12-31")
        composeRule.onNodeWithTag("savings-account-1").performClick()
        composeRule.onNodeWithText("Create goal").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.createdInputs.isNotEmpty()
        }
        assertEquals(120_000L, repository.createdInputs.single().targetCents)
        assertEquals("EUR", repository.createdInputs.single().currencyCode)
        assertEquals(1L, repository.createdInputs.single().accountId)

        composeRule.onNodeWithText("Emergency fund").assertIsDisplayed()
        composeRule.onNodeWithTag("savings-deposit-1").performClick()
        composeRule.onNodeWithTag("savings-operation-amount").performTextReplacement("25.50")
        composeRule.onNodeWithTag("savings-operation-note").performTextReplacement("Bonus")
        composeRule.onNodeWithText("Deposit").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.deposits.isNotEmpty()
        }
        assertEquals(2_550L, repository.deposits.single().amountCents)

        composeRule.onNodeWithTag("savings-withdraw-1").performClick()
        composeRule.onNodeWithTag("savings-operation-amount").performTextReplacement("5.00")
        composeRule.onNodeWithText("Withdraw").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.withdrawals.isNotEmpty()
        }
        assertEquals(500L, repository.withdrawals.single().amountCents)

        composeRule.onNodeWithTag("savings-history-1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.historyRequests == 1
        }
        composeRule.onNodeWithText("Goal history").assertIsDisplayed()
        composeRule.onNodeWithText("+100.00 USD").assertIsDisplayed()
    }
}

private class FakeSavingsGoalsRepository(
    initialGoals: List<SavingsGoal> = emptyList(),
    private val initialHistory: List<SavingsGoalHistoryEntry> = emptyList(),
) : SavingsGoalsRepository {
    private val goals = initialGoals.toMutableList()
    val createdInputs = mutableListOf<CreateSavingsGoalInput>()
    val deposits = mutableListOf<SavingsGoalOperationInput>()
    val withdrawals = mutableListOf<SavingsGoalOperationInput>()
    var historyRequests = 0
        private set

    override suspend fun listGoals(profileId: Long): List<SavingsGoal> = goals.toList()

    override suspend fun getGoal(profileId: Long, goalId: Long): SavingsGoal {
        return goals.first { it.id == goalId }
    }

    override suspend fun createGoal(profileId: Long, input: CreateSavingsGoalInput): SavingsGoal {
        createdInputs += input
        val goal = SavingsGoal(
            id = (goals.maxOfOrNull { it.id } ?: 0L) + 1L,
            profileId = profileId,
            name = input.name,
            targetCents = input.targetCents,
            currentCents = 0,
            currencyCode = input.currencyCode,
            deadlineDate = input.deadlineDate,
            accountId = input.accountId,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        goals += goal
        return goal
    }

    override suspend fun updateGoal(
        profileId: Long,
        goalId: Long,
        input: UpdateSavingsGoalInput,
    ): SavingsGoal {
        val index = goals.indexOfFirst { it.id == goalId }
        val existing = goals[index]
        val updated = existing.copy(
            name = input.name ?: existing.name,
            targetCents = input.targetCents ?: existing.targetCents,
            deadlineDate = if (input.clearDeadline) null else input.deadlineDate ?: existing.deadlineDate,
            accountId = if (input.clearLinkedAccount) null else input.accountId ?: existing.accountId,
        )
        goals[index] = updated
        return updated
    }

    override suspend fun deleteGoal(profileId: Long, goalId: Long) {
        goals.removeAll { it.id == goalId }
    }

    override suspend fun deposit(
        profileId: Long,
        goalId: Long,
        input: SavingsGoalOperationInput,
    ): SavingsGoal {
        deposits += input
        return updateCurrent(goalId, input.amountCents)
    }

    override suspend fun withdraw(
        profileId: Long,
        goalId: Long,
        input: SavingsGoalOperationInput,
    ): SavingsGoal {
        withdrawals += input
        return updateCurrent(goalId, -input.amountCents)
    }

    override suspend fun listHistory(profileId: Long, goalId: Long): List<SavingsGoalHistoryEntry> {
        historyRequests += 1
        return initialHistory
    }

    private fun updateCurrent(goalId: Long, deltaCents: Long): SavingsGoal {
        val index = goals.indexOfFirst { it.id == goalId }
        val updated = goals[index].copy(currentCents = goals[index].currentCents + deltaCents)
        goals[index] = updated
        return updated
    }
}

private class FakeAccountsRepository : AccountsRepository {
    override suspend fun listAccounts(profileId: Long): List<Account> = accountsFixture

    override suspend fun getAccount(profileId: Long, accountId: Long): Account {
        return accountsFixture.first { it.id == accountId }
    }

    override suspend fun getDefaultAccount(profileId: Long): Account = accountsFixture.first()

    override suspend fun createAccount(profileId: Long, input: CreateAccountInput): Account {
        throw UnsupportedOperationException()
    }

    override suspend fun updateAccount(
        profileId: Long,
        accountId: Long,
        input: UpdateAccountInput,
    ): Account {
        throw UnsupportedOperationException()
    }

    override suspend fun deleteAccount(profileId: Long, accountId: Long) {
        throw UnsupportedOperationException()
    }

    override suspend fun setDefaultAccount(profileId: Long, accountId: Long): Account {
        throw UnsupportedOperationException()
    }
}

private val profileFixture = LocalProfile(
    id = 1,
    label = "Personal",
    languageCode = "en",
    displayCurrencies = emptyList(),
    notifyBudgetAlerts = true,
    notifyRecurringReminders = false,
    notifyWeeklySummary = false,
    notifyGoalMilestones = false,
    statsChartStyle = "donut",
    animateNumbers = null,
    theme = "system",
    hideAmounts = false,
    createdAtEpochMillis = 1,
    updatedAtEpochMillis = 1,
)

private val accountsFixture = listOf(
    Account(
        id = 1,
        profileId = 1,
        name = "Main savings account",
        icon = "wallet",
        color = "#0EA5E9",
        type = AccountType.Savings,
        currencyCode = "USD",
        isDefault = true,
        includeInTotal = true,
        balanceCents = 250_000,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
)

private fun goalFixture(
    id: Long,
    name: String,
    currentCents: Long = 50_000,
    targetCents: Long = 100_000,
): SavingsGoal {
    return SavingsGoal(
        id = id,
        profileId = 1,
        name = name,
        targetCents = targetCents,
        currentCents = currentCents,
        currencyCode = "USD",
        deadlineDate = "2026-12-31",
        accountId = 1,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}

private fun historyFixture(
    id: Long,
    type: SavingsGoalTransactionType,
    amountCents: Long,
): SavingsGoalHistoryEntry {
    return SavingsGoalHistoryEntry(
        id = id,
        profileId = 1,
        goalId = 1,
        type = type,
        amountCents = amountCents,
        createdAtEpochMillis = 1,
    )
}
