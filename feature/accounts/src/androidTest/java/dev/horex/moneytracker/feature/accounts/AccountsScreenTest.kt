package dev.horex.moneytracker.feature.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import dev.horex.moneytracker.core.transactions.BalanceAdjustmentInput
import dev.horex.moneytracker.core.transactions.CreateTransactionInput
import dev.horex.moneytracker.core.transactions.MoneyTransaction
import dev.horex.moneytracker.core.transactions.TransactionPage
import dev.horex.moneytracker.core.transactions.TransactionQuery
import dev.horex.moneytracker.core.transactions.TransactionType
import dev.horex.moneytracker.core.transactions.TransactionsRepository
import dev.horex.moneytracker.core.transactions.UpdateTransactionInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accountsScreenRendersOnSmallWidth() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 560.dp)) {
                    AccountsScreen(
                        state = AccountsUiState(accounts = accountsFixture),
                        onRetry = {},
                        onAddAccount = {},
                        onEditAccount = {},
                        onDeleteAccount = {},
                        onSetDefault = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Accounts").assertIsDisplayed()
        composeRule.onNodeWithText("Main card").assertIsDisplayed()
        composeRule.onNodeWithText("Pocket cash").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add account").assertIsDisplayed()
    }

    @Test
    fun accountsScreenShowsConstraintError() {
        composeRule.setContent {
            MoneyTrackerTheme {
                AccountsScreen(
                    state = AccountsUiState(
                        accounts = accountsFixture,
                        error = AccountsError.HasTransactions,
                    ),
                    onRetry = {},
                    onAddAccount = {},
                    onEditAccount = {},
                    onDeleteAccount = {},
                    onSetDefault = {},
                )
            }
        }

        composeRule.onNodeWithText("This account has transactions and cannot be deleted.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun accountsRouteKeepsNegativeTargetBalanceForAdjustment() {
        val account = accountsFixture.first().copy(
            name = "Credit card",
            type = AccountType.Credit,
            balanceCents = -1_250,
        )
        val transactionsRepository = FakeTransactionsRepository()

        composeRule.setContent {
            MoneyTrackerTheme {
                AccountsRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    accountsRepository = FakeAccountsRepository(account),
                    transactionsRepository = transactionsRepository,
                )
            }
        }

        composeRule.onNodeWithText("Credit card").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit account").performClick()
        composeRule.onNodeWithText("-12.50").assertIsDisplayed()
        composeRule.onNodeWithText("-12.50").performTextReplacement("-15.00")
        composeRule.onNodeWithText("Decrease by 2.50 USD").assertIsDisplayed()
        composeRule.onNodeWithText("Apply adjustment").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            transactionsRepository.lastAdjustmentDeltaCents == -250L
        }
        assertEquals(-250L, transactionsRepository.lastAdjustmentDeltaCents)
    }

    @Test
    fun accountsRouteCreatesAccountWithCurrencyPickerSelection() {
        val accountsRepository = FakeAccountsRepository(accountsFixture.first())

        composeRule.setContent {
            MoneyTrackerTheme {
                AccountsRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    accountsRepository = accountsRepository,
                    transactionsRepository = FakeTransactionsRepository(),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add account").performClick()
        composeRule.onNodeWithTag("accounts-name").performTextReplacement("Euro cash")
        composeRule.onNodeWithTag("accounts-currency").performClick()
        composeRule.onNodeWithTag("accounts-currency-search").performTextReplacement("Euro")
        composeRule.onNodeWithTag("accounts-currency-option-EUR").performClick()
        composeRule.onNodeWithText("Create account").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            accountsRepository.createdInputs.isNotEmpty()
        }
        assertEquals("Euro cash", accountsRepository.createdInputs.single().name)
        assertEquals("EUR", accountsRepository.createdInputs.single().currencyCode)
    }

    private companion object {
        val profileFixture = LocalProfile(
            id = 1,
            label = "Personal",
            languageCode = "en",
            displayCurrencies = emptyList(),
            notifyBudgetAlerts = false,
            notifyRecurringReminders = false,
            notifyWeeklySummary = false,
            notifyGoalMilestones = false,
            statsChartStyle = "line",
            animateNumbers = null,
            theme = "system",
            hideAmounts = false,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )

        val accountsFixture = listOf(
            Account(
                id = 1,
                profileId = 1,
                name = "Main card",
                icon = "checking",
                color = "#6366F1",
                type = AccountType.Checking,
                currencyCode = "USD",
                isDefault = true,
                includeInTotal = true,
                balanceCents = 125_50,
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
            Account(
                id = 2,
                profileId = 1,
                name = "Pocket cash",
                icon = "cash",
                color = "#F59E0B",
                type = AccountType.Cash,
                currencyCode = "USD",
                isDefault = false,
                includeInTotal = false,
                balanceCents = 42_00,
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
        )
    }
}

private class FakeAccountsRepository(
    private val account: Account,
) : AccountsRepository {
    val createdInputs = mutableListOf<CreateAccountInput>()

    override suspend fun listAccounts(profileId: Long): List<Account> {
        return listOf(account)
    }

    override suspend fun getAccount(profileId: Long, accountId: Long): Account {
        return account
    }

    override suspend fun getDefaultAccount(profileId: Long): Account {
        return account
    }

    override suspend fun createAccount(profileId: Long, input: CreateAccountInput): Account {
        createdInputs += input
        return account
    }

    override suspend fun updateAccount(profileId: Long, accountId: Long, input: UpdateAccountInput): Account {
        return account
    }

    override suspend fun setDefaultAccount(profileId: Long, accountId: Long): Account {
        return account
    }

    override suspend fun deleteAccount(profileId: Long, accountId: Long) = Unit
}

private class FakeTransactionsRepository : TransactionsRepository {
    var lastAdjustmentDeltaCents: Long? = null
        private set

    override suspend fun applyBalanceAdjustment(
        profileId: Long,
        input: BalanceAdjustmentInput,
    ): MoneyTransaction {
        lastAdjustmentDeltaCents = input.deltaCents
        return moneyTransactionFixture(
            profileId = profileId,
            accountId = input.accountId,
            amountCents = kotlin.math.abs(input.deltaCents),
            type = if (input.deltaCents < 0) TransactionType.Expense else TransactionType.Income,
        )
    }

    override suspend fun addTransaction(profileId: Long, input: CreateTransactionInput): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun getTransaction(profileId: Long, transactionId: Long): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun listTransactions(profileId: Long, query: TransactionQuery): TransactionPage {
        return TransactionPage(transactions = emptyList(), totalPages = 0, currentPage = query.page)
    }

    override suspend fun updateTransaction(
        profileId: Long,
        transactionId: Long,
        input: UpdateTransactionInput,
    ): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun deleteTransaction(profileId: Long, transactionId: Long) = Unit

    private fun moneyTransactionFixture(
        profileId: Long,
        accountId: Long,
        amountCents: Long,
        type: TransactionType,
    ): MoneyTransaction {
        return MoneyTransaction(
            id = 1,
            profileId = profileId,
            type = type,
            amountCents = amountCents,
            categoryId = 1,
            categoryName = "Adjustment",
            categoryIcon = "adjustment",
            categoryColor = "#64748B",
            accountId = accountId,
            accountName = "Credit card",
            note = "",
            currencyCode = "USD",
            snapshotDate = "2026-07-02",
            createdAtEpochMillis = 1,
            isAdjustment = true,
        )
    }
}
