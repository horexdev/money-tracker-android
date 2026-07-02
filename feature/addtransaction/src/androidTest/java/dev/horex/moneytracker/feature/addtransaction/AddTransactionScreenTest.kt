package dev.horex.moneytracker.feature.addtransaction

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import dev.horex.moneytracker.core.transactions.TransactionPage
import dev.horex.moneytracker.core.transactions.TransactionQuery
import dev.horex.moneytracker.core.transactions.TransactionType
import dev.horex.moneytracker.core.transactions.TransactionsRepository
import dev.horex.moneytracker.core.transactions.UpdateTransactionInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class AddTransactionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addTransactionRouteSavesExpensePayload() {
        val transactionsRepository = FakeTransactionsRepository()
        var saved = false

        composeRule.setContent {
            MoneyTrackerTheme {
                AddTransactionRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    transactionsRepository = transactionsRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                    onTransactionSaved = { saved = true },
                    todayProvider = { LocalDate.parse("2026-07-02") },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Food").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("add-transaction-amount").performTextReplacement("15,25")
        composeRule.onNodeWithTag("add-transaction-category-1").performClick()
        composeRule.onNodeWithTag("add-transaction-note").performTextReplacement("Lunch")
        composeRule.onNodeWithTag("add-transaction-save").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            transactionsRepository.lastInput != null && saved
        }

        val input = requireNotNull(transactionsRepository.lastInput)
        assertEquals(TransactionType.Expense, input.type)
        assertEquals(1_525L, input.amountCents)
        assertEquals(1L, input.accountId)
        assertEquals(1L, input.categoryId)
        assertEquals("Lunch", input.note)
        assertEquals(
            LocalDate.parse("2026-07-02")
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
            input.createdAtEpochMillis,
        )
    }

    @Test
    fun addTransactionRouteFiltersIncomeCategoriesAndSavesIncome() {
        val transactionsRepository = FakeTransactionsRepository()

        composeRule.setContent {
            MoneyTrackerTheme {
                AddTransactionRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    transactionsRepository = transactionsRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                    todayProvider = { LocalDate.parse("2026-07-02") },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Food").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("add-transaction-type-Income").performClick()

        composeRule.onAllNodesWithText("Food").assertCountEquals(0)
        composeRule.onNodeWithText("Salary").assertIsDisplayed()
        composeRule.onNodeWithTag("add-transaction-amount").performTextReplacement("2500")
        composeRule.onNodeWithTag("add-transaction-category-2").performClick()
        composeRule.onNodeWithTag("add-transaction-save").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            transactionsRepository.lastInput != null
        }

        val input = requireNotNull(transactionsRepository.lastInput)
        assertEquals(TransactionType.Income, input.type)
        assertEquals(250_000L, input.amountCents)
        assertEquals(2L, input.categoryId)
    }

    @Test
    fun addTransactionRouteShowsValidationErrorsBeforeSaving() {
        val transactionsRepository = FakeTransactionsRepository()

        composeRule.setContent {
            MoneyTrackerTheme {
                AddTransactionRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    transactionsRepository = transactionsRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                    todayProvider = { LocalDate.parse("2026-07-02") },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Food").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("add-transaction-save").performClick()
        composeRule.onNodeWithText("Enter an amount greater than zero.").assertIsDisplayed()

        composeRule.onNodeWithTag("add-transaction-amount").performTextReplacement("12")
        composeRule.onNodeWithTag("add-transaction-save").performClick()
        composeRule.onNodeWithText("Choose a category.").assertIsDisplayed()
        assertTrue(transactionsRepository.inputs.isEmpty())
    }
}

private class FakeTransactionsRepository : TransactionsRepository {
    val inputs = mutableListOf<CreateTransactionInput>()
    val lastInput: CreateTransactionInput?
        get() = inputs.lastOrNull()

    override suspend fun addTransaction(profileId: Long, input: CreateTransactionInput): MoneyTransaction {
        inputs += input
        val category = categoriesFixture.first { it.id == input.categoryId }
        val account = accountsFixture.first { it.id == input.accountId }
        return MoneyTransaction(
            id = inputs.size.toLong(),
            profileId = profileId,
            type = input.type,
            amountCents = input.amountCents,
            categoryId = category.id,
            categoryName = category.name,
            categoryIcon = category.icon,
            categoryColor = category.color,
            accountId = account.id,
            accountName = account.name,
            note = input.note,
            currencyCode = account.currencyCode,
            snapshotDate = "2026-07-02",
            createdAtEpochMillis = input.createdAtEpochMillis ?: 1,
            isAdjustment = false,
        )
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
        throw UnsupportedOperationException()
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

private class FakeAccountsRepository : AccountsRepository {
    override suspend fun listAccounts(profileId: Long): List<Account> {
        return accountsFixture
    }

    override suspend fun getAccount(profileId: Long, accountId: Long): Account {
        return accountsFixture.first { it.id == accountId }
    }

    override suspend fun getDefaultAccount(profileId: Long): Account {
        return accountsFixture.first { it.isDefault }
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
    Account(
        id = 2,
        profileId = 1,
        name = "Cash",
        icon = "cash",
        color = "#F59E0B",
        type = AccountType.Cash,
        currencyCode = "USD",
        isDefault = false,
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
        name = "Food",
        icon = "shopping-bag",
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
    Category(
        id = 3,
        profileId = 1,
        name = "Shared",
        icon = "tag",
        type = CategoryType.Both,
        color = "#6366F1",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
)
