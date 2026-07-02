package dev.horex.moneytracker.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.accounts.Account
import dev.horex.moneytracker.core.accounts.AccountType
import dev.horex.moneytracker.core.accounts.AccountsRepository
import dev.horex.moneytracker.core.accounts.CreateAccountInput
import dev.horex.moneytracker.core.accounts.UpdateAccountInput
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.categories.CreateCategoryInput
import dev.horex.moneytracker.core.categories.UpdateCategoryInput
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.transactions.BalanceAdjustmentInput
import dev.horex.moneytracker.core.transactions.CreateTransactionInput
import dev.horex.moneytracker.core.transactions.MoneyTransaction
import dev.horex.moneytracker.core.transactions.TransactionLinkedToTransferException
import dev.horex.moneytracker.core.transactions.TransactionPage
import dev.horex.moneytracker.core.transactions.TransactionQuery
import dev.horex.moneytracker.core.transactions.TransactionType
import dev.horex.moneytracker.core.transactions.TransactionsRepository
import dev.horex.moneytracker.core.transactions.UpdateTransactionInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historyScreenRendersOnSmallWidth() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 840.dp)) {
                    HistoryScreen(
                        state = HistoryUiState(
                            transactions = transactionsFixture,
                            accounts = accountsFixture,
                            categories = categoriesFixture,
                            currentPage = 1,
                            totalPages = 2,
                        ),
                        filters = HistoryFilters(),
                        searchDraft = "",
                        onSearchDraftChange = {},
                        onApplySearch = {},
                        onClearFilters = {},
                        onSelectAccount = {},
                        onSelectCategory = {},
                        onLoadMore = {},
                        onRetry = {},
                        onEditTransaction = {},
                        onDeleteTransaction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("Search transactions").assertIsDisplayed()
        composeRule.onNodeWithText("July apartment").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly salary").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun historyRouteAppliesSearchOnRepositoryQuery() {
        val transactionsRepository = FakeTransactionsRepository(transactionsFixture)

        composeRule.setContent {
            MoneyTrackerTheme {
                HistoryRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    transactionsRepository = transactionsRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                )
            }
        }

        composeRule.onNodeWithText("July apartment").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("salary")
        composeRule.onNodeWithText("Apply filters").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            transactionsRepository.lastQuery?.searchText == "salary"
        }
        assertEquals("salary", transactionsRepository.lastQuery?.searchText)
        composeRule.onNodeWithText("Monthly salary").assertIsDisplayed()
    }

    @Test
    fun historyRouteShowsLinkedTransferDeleteError() {
        val transactionsRepository = FakeTransactionsRepository(
            transactions = transactionsFixture.take(1),
            rejectDelete = true,
        )

        composeRule.setContent {
            MoneyTrackerTheme {
                HistoryRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    transactionsRepository = transactionsRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                )
            }
        }

        composeRule.onNodeWithText("July apartment").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete transaction").performClick()
        composeRule.onNodeWithText("Delete transaction?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete transaction").performClick()

        composeRule.onNodeWithText(
            "Linked transfer transactions cannot be edited or deleted from history.",
        ).assertIsDisplayed()
    }
}

private class FakeTransactionsRepository(
    transactions: List<MoneyTransaction>,
    private val rejectDelete: Boolean = false,
) : TransactionsRepository {
    private var transactions = transactions
    var lastQuery: TransactionQuery? = null
        private set

    override suspend fun listTransactions(profileId: Long, query: TransactionQuery): TransactionPage {
        lastQuery = query
        val filtered = transactions.filter { transaction ->
            val search = query.searchText?.lowercase().orEmpty()
            val matchesSearch = search.isEmpty() ||
                transaction.note.lowercase().contains(search) ||
                transaction.categoryName.lowercase().contains(search) ||
                transaction.accountName.lowercase().contains(search)
            val matchesAccount = query.accountId == null || transaction.accountId == query.accountId
            val matchesCategory = query.categoryId == null || transaction.categoryId == query.categoryId
            matchesSearch && matchesAccount && matchesCategory
        }
        val totalPages = max(1, (filtered.size + query.pageSize - 1) / query.pageSize)
        val currentPage = query.page.coerceIn(1, totalPages)
        val pageItems = filtered
            .drop((currentPage - 1) * query.pageSize)
            .take(query.pageSize)
        return TransactionPage(
            transactions = pageItems,
            totalPages = totalPages,
            currentPage = currentPage,
        )
    }

    override suspend fun updateTransaction(
        profileId: Long,
        transactionId: Long,
        input: UpdateTransactionInput,
    ): MoneyTransaction {
        val existing = transactions.first { it.id == transactionId }
        val category = categoriesFixture.first { it.id == input.categoryId }
        val updated = existing.copy(
            amountCents = input.amountCents,
            categoryId = category.id,
            categoryName = category.name,
            categoryColor = category.color,
            note = input.note,
            createdAtEpochMillis = input.createdAtEpochMillis ?: existing.createdAtEpochMillis,
        )
        transactions = transactions.map { if (it.id == transactionId) updated else it }
        return updated
    }

    override suspend fun deleteTransaction(profileId: Long, transactionId: Long) {
        if (rejectDelete) {
            throw TransactionLinkedToTransferException()
        }
        transactions = transactions.filterNot { it.id == transactionId }
    }

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
        return transactions.first { it.id == transactionId }
    }
}

private class FakeAccountsRepository : AccountsRepository {
    override suspend fun listAccounts(profileId: Long): List<Account> {
        return accountsFixture
    }

    override suspend fun getAccount(profileId: Long, accountId: Long): Account {
        return accountsFixture.first { it.id == accountId }
    }

    override suspend fun getDefaultAccount(profileId: Long): Account {
        return accountsFixture.first()
    }

    override suspend fun createAccount(profileId: Long, input: CreateAccountInput): Account {
        throw UnsupportedOperationException()
    }

    override suspend fun updateAccount(profileId: Long, accountId: Long, input: UpdateAccountInput): Account {
        throw UnsupportedOperationException()
    }

    override suspend fun setDefaultAccount(profileId: Long, accountId: Long): Account {
        throw UnsupportedOperationException()
    }

    override suspend fun deleteAccount(profileId: Long, accountId: Long) {
        throw UnsupportedOperationException()
    }
}

private class FakeCategoriesRepository : CategoriesRepository {
    override suspend fun listCategories(
        profileId: Long,
        type: CategoryType?,
        sortOrder: CategorySortOrder,
    ): List<Category> {
        return categoriesFixture.filter { category ->
            type == null || category.type == type || category.type == CategoryType.Both
        }
    }

    override suspend fun getCategory(profileId: Long, categoryId: Long): Category {
        return categoriesFixture.first { it.id == categoryId }
    }

    override suspend fun getProtectedCategoryByType(profileId: Long, type: CategoryType): Category {
        throw UnsupportedOperationException()
    }

    override suspend fun hasEditableCategories(profileId: Long): Boolean {
        return categoriesFixture.isNotEmpty()
    }

    override suspend fun createCategory(profileId: Long, input: CreateCategoryInput): Category {
        throw UnsupportedOperationException()
    }

    override suspend fun updateCategory(profileId: Long, categoryId: Long, input: UpdateCategoryInput): Category {
        throw UnsupportedOperationException()
    }

    override suspend fun deleteCategory(profileId: Long, categoryId: Long) {
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
        balanceCents = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
)

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Rent",
        icon = "home",
        type = CategoryType.Expense,
        color = "#EF4444",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
    Category(
        id = 2,
        profileId = 1,
        name = "Salary",
        icon = "salary",
        type = CategoryType.Income,
        color = "#10B981",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
)

private val transactionsFixture = listOf(
    MoneyTransaction(
        id = 1,
        profileId = 1,
        type = TransactionType.Expense,
        amountCents = 950_00,
        categoryId = 1,
        categoryName = "Rent",
        categoryIcon = "home",
        categoryColor = "#EF4444",
        accountId = 1,
        accountName = "Main card",
        note = "July apartment",
        currencyCode = "USD",
        snapshotDate = "2026-07-01",
        createdAtEpochMillis = 1_782_864_000_000L,
        isAdjustment = false,
    ),
    MoneyTransaction(
        id = 2,
        profileId = 1,
        type = TransactionType.Income,
        amountCents = 3_500_00,
        categoryId = 2,
        categoryName = "Salary",
        categoryIcon = "salary",
        categoryColor = "#10B981",
        accountId = 1,
        accountName = "Main card",
        note = "Monthly salary",
        currencyCode = "USD",
        snapshotDate = "2026-07-02",
        createdAtEpochMillis = 1_782_950_400_000L,
        isAdjustment = false,
    ),
)
