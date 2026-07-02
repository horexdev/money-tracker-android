package dev.horex.moneytracker.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.balance.BalanceAccount
import dev.horex.moneytracker.core.balance.BalanceCurrency
import dev.horex.moneytracker.core.balance.BalanceQuery
import dev.horex.moneytracker.core.balance.BalanceSnapshot
import dev.horex.moneytracker.core.balance.BalancesRepository
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
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeRouteShowsBalanceSummariesAndRecentTransactions() {
        val transactionsRepository = FakeTransactionsRepository(transactionsFixture)

        composeRule.setContent {
            MoneyTrackerTheme {
                HomeRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    balancesRepository = FakeBalancesRepository(),
                    transactionsRepository = transactionsRepository,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            transactionsRepository.queries.isNotEmpty()
        }

        composeRule.onNodeWithTag("home-balance-hero").assertIsDisplayed()
        composeRule.onNodeWithText("2157.50 USD").assertIsDisplayed()
        composeRule.onNodeWithTag("home-income-summary").assertIsDisplayed()
        composeRule.onNodeWithText("2800.00 USD").assertIsDisplayed()
        composeRule.onNodeWithTag("home-expense-summary").assertIsDisplayed()
        composeRule.onNodeWithText("642.50 USD").assertIsDisplayed()
        composeRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeRule.onNodeWithText("-42.50 USD").assertIsDisplayed()
        assertEquals(1L, transactionsRepository.queries.last().accountId)
        assertEquals(5, transactionsRepository.queries.last().pageSize)
    }

    @Test
    fun homeRouteMasksAmountsWhenProfileHidesAmounts() {
        composeRule.setContent {
            MoneyTrackerTheme {
                HomeRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture.copy(hideAmounts = true) },
                    balancesRepository = FakeBalancesRepository(),
                    transactionsRepository = FakeTransactionsRepository(transactionsFixture),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("**** USD").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithText("2157.50 USD").assertCountEquals(0)
        assertEquals(4, composeRule.onAllNodesWithText("**** USD").fetchSemanticsNodes().size)
    }

    @Test
    fun homeRouteShowsEmptyStateAndReloadsAllAccounts() {
        val transactionsRepository = FakeTransactionsRepository(emptyList())

        composeRule.setContent {
            MoneyTrackerTheme {
                HomeRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    balancesRepository = FakeBalancesRepository(),
                    transactionsRepository = transactionsRepository,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            transactionsRepository.queries.isNotEmpty()
        }
        composeRule.onNodeWithTag("home-account-all").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            transactionsRepository.queries.last().accountId == null
        }

        composeRule.onNodeWithTag("home-empty-state").assertIsDisplayed()
        composeRule.onNodeWithText("No transactions yet").assertIsDisplayed()
    }
}

private class FakeBalancesRepository : BalancesRepository {
    override suspend fun listAccountBalances(profileId: Long): List<BalanceAccount> {
        return accountsFixture
    }

    override suspend fun getBalance(profileId: Long, query: BalanceQuery): BalanceSnapshot {
        val accountId = query.accountId
        val account = accountsFixture.firstOrNull { it.id == accountId }
        val totals = if (accountId == null || accountId == 1L) {
            BalanceCurrency(
                currencyCode = "USD",
                incomeCents = 280_000L,
                expenseCents = 64_250L,
                netCents = 215_750L,
            )
        } else {
            BalanceCurrency(
                currencyCode = "USD",
                incomeCents = 0L,
                expenseCents = 0L,
                netCents = account?.balanceCents ?: 0L,
            )
        }
        return BalanceSnapshot(
            profileId = profileId,
            accountId = accountId,
            baseCurrencyCode = "USD",
            accountBalances = accountsFixture,
            byCurrency = listOf(totals),
            displayConversions = emptyList(),
            totalInBaseCents = account?.balanceCents ?: 215_750L,
        )
    }
}

private class FakeTransactionsRepository(
    private val transactions: List<MoneyTransaction>,
) : TransactionsRepository {
    val queries = mutableListOf<TransactionQuery>()

    override suspend fun addTransaction(profileId: Long, input: CreateTransactionInput): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun applyBalanceAdjustment(
        profileId: Long,
        input: BalanceAdjustmentInput,
    ): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun getTransaction(profileId: Long, transactionId: Long): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun listTransactions(profileId: Long, query: TransactionQuery): TransactionPage {
        queries += query
        val filtered = query.accountId?.let { accountId ->
            transactions.filter { it.accountId == accountId }
        } ?: transactions
        return TransactionPage(
            transactions = filtered.take(query.pageSize),
            totalPages = 1,
            currentPage = query.page,
        )
    }

    override suspend fun updateTransaction(
        profileId: Long,
        transactionId: Long,
        input: UpdateTransactionInput,
    ): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun deleteTransaction(profileId: Long, transactionId: Long) {
        throw UnsupportedOperationException()
    }
}

private val profileFixture = LocalProfile(
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

private val accountsFixture = listOf(
    BalanceAccount(
        id = 1,
        profileId = 1,
        name = "Main card",
        icon = "checking",
        color = "#6366F1",
        type = "checking",
        currencyCode = "USD",
        isDefault = true,
        includeInTotal = true,
        balanceCents = 215_750L,
    ),
    BalanceAccount(
        id = 2,
        profileId = 1,
        name = "Cash",
        icon = "cash",
        color = "#F59E0B",
        type = "cash",
        currencyCode = "USD",
        isDefault = false,
        includeInTotal = true,
        balanceCents = 5_000L,
    ),
)

private val transactionsFixture = listOf(
    MoneyTransaction(
        id = 1,
        profileId = 1,
        type = TransactionType.Expense,
        amountCents = 4_250L,
        categoryId = 1,
        categoryName = "Groceries",
        categoryIcon = "cart",
        categoryColor = "#EF4444",
        accountId = 1,
        accountName = "Main card",
        note = "Weekly market",
        currencyCode = "USD",
        snapshotDate = "2026-07-02",
        createdAtEpochMillis = 1_782_950_400_000L,
        isAdjustment = false,
    ),
)
