package dev.horex.moneytracker.feature.recurring

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
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.categories.CreateCategoryInput
import dev.horex.moneytracker.core.categories.UpdateCategoryInput
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.recurring.CreateRecurringTransactionInput
import dev.horex.moneytracker.core.recurring.ProcessRecurringDueResult
import dev.horex.moneytracker.core.recurring.RecurringFrequency
import dev.horex.moneytracker.core.recurring.RecurringTransaction
import dev.horex.moneytracker.core.recurring.RecurringTransactionsRepository
import dev.horex.moneytracker.core.recurring.UpdateRecurringTransactionInput
import dev.horex.moneytracker.core.transactions.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RecurringScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recurringScreenRendersNextRunAndInactiveStateOnSmallWidth() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 760.dp)) {
                    RecurringScreen(
                        state = RecurringUiState(
                            recurring = listOf(
                                recurringFixture(
                                    id = 1,
                                    note = "Rent",
                                    categoryName = "Housing",
                                    amountCents = 120_000,
                                    nextRunAtEpochMillis = "2026-08-01".toStartOfDayEpochMillis(),
                                    isActive = true,
                                ),
                                recurringFixture(
                                    id = 2,
                                    note = "Salary",
                                    categoryName = "Salary",
                                    type = TransactionType.Income,
                                    amountCents = 350_000,
                                    nextRunAtEpochMillis = "2026-07-15".toStartOfDayEpochMillis(),
                                    isActive = false,
                                ),
                            ),
                            accounts = accountsFixture,
                            categories = categoriesFixture,
                        ),
                        onRetry = {},
                        onAddRecurring = {},
                        onEditRecurring = {},
                        onToggleRecurring = {},
                        onDeleteRecurring = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Recurring").assertIsDisplayed()
        composeRule.onNodeWithText("Rent").assertIsDisplayed()
        composeRule.onNodeWithText("Next run: 2026-08-01").assertIsDisplayed()
        composeRule.onNodeWithTag("recurring-list")
            .performScrollToNode(hasTestTag("recurring-card-2"))
        composeRule.onNodeWithText("Salary").assertIsDisplayed()
        composeRule.onNodeWithText("Inactive").assertIsDisplayed()
        composeRule.onNodeWithText("Inactive - schedule is paused").assertIsDisplayed()
    }

    @Test
    fun recurringRouteCreatesRecurringTransaction() {
        val recurringRepository = FakeRecurringTransactionsRepository()

        composeRule.setContent {
            MoneyTrackerTheme {
                RecurringRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    recurringRepository = recurringRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                    todayProvider = { LocalDate.of(2026, 7, 3) },
                )
            }
        }

        composeRule.onNodeWithText("No recurring transactions").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add recurring").performClick()
        composeRule.onNodeWithTag("recurring-amount").performTextReplacement("42.50")
        composeRule.onNodeWithTag("recurring-frequency-weekly").performClick()
        composeRule.onNodeWithTag("recurring-next-run").performTextReplacement("2026-07-10")
        composeRule.onNodeWithTag("recurring-note").performTextReplacement("Weekly market")
        composeRule.onNodeWithText("Create recurring").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            recurringRepository.createdInputs.isNotEmpty()
        }

        val input = recurringRepository.createdInputs.single()
        assertEquals(TransactionType.Expense, input.type)
        assertEquals(4_250L, input.amountCents)
        assertEquals(1L, input.accountId)
        assertEquals(1L, input.categoryId)
        assertEquals(RecurringFrequency.Weekly, input.frequency)
        assertEquals("Weekly market", input.note)
        assertEquals("2026-07-10".toStartOfDayEpochMillis(), input.nextRunAtEpochMillis)
    }

    @Test
    fun recurringRouteTogglesEditsAndDeletesRecurringTransaction() {
        val recurringRepository = FakeRecurringTransactionsRepository(
            initialRecurring = listOf(
                recurringFixture(
                    id = 1,
                    note = "Rent",
                    categoryName = "Housing",
                    amountCents = 120_000,
                    nextRunAtEpochMillis = "2026-08-01".toStartOfDayEpochMillis(),
                    isActive = true,
                ),
            ),
        )

        composeRule.setContent {
            MoneyTrackerTheme {
                RecurringRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    recurringRepository = recurringRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                    todayProvider = { LocalDate.of(2026, 7, 3) },
                )
            }
        }

        composeRule.onNodeWithText("Rent").assertIsDisplayed()
        composeRule.onNodeWithTag("recurring-toggle-1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            recurringRepository.toggledRecurringIds == listOf(1L)
        }
        assertFalse(recurringRepository.currentRecurring.single().isActive)

        composeRule.onNodeWithContentDescription("Edit recurring").performClick()
        composeRule.onNodeWithTag("recurring-amount").performTextReplacement("1300.00")
        composeRule.onNodeWithTag("recurring-next-run").performTextReplacement("2026-09-01")
        composeRule.onNodeWithTag("recurring-note").performTextReplacement("Updated rent")
        composeRule.onNodeWithText("Save recurring").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            recurringRepository.updatedInputs.any { it.amountCents == 130_000L }
        }

        val update = recurringRepository.updatedInputs.last()
        assertEquals("Updated rent", update.note)
        assertEquals("2026-09-01".toStartOfDayEpochMillis(), update.nextRunAtEpochMillis)

        composeRule.onNodeWithContentDescription("Delete recurring").performClick()
        composeRule.onNodeWithText("Delete recurring").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            recurringRepository.deletedRecurringIds == listOf(1L)
        }
    }
}

private class FakeRecurringTransactionsRepository(
    initialRecurring: List<RecurringTransaction> = emptyList(),
) : RecurringTransactionsRepository {
    private val recurring = initialRecurring.toMutableList()
    val createdInputs = mutableListOf<CreateRecurringTransactionInput>()
    val updatedInputs = mutableListOf<UpdateRecurringTransactionInput>()
    val toggledRecurringIds = mutableListOf<Long>()
    val deletedRecurringIds = mutableListOf<Long>()
    val currentRecurring: List<RecurringTransaction>
        get() = recurring.toList()

    override suspend fun listRecurring(profileId: Long): List<RecurringTransaction> {
        return recurring.toList()
    }

    override suspend fun getRecurring(profileId: Long, recurringId: Long): RecurringTransaction {
        return recurring.first { it.id == recurringId }
    }

    override suspend fun createRecurring(
        profileId: Long,
        input: CreateRecurringTransactionInput,
    ): RecurringTransaction {
        createdInputs += input
        val category = categoriesFixture.first { it.id == input.categoryId }
        val account = accountsFixture.first { it.id == input.accountId }
        val created = RecurringTransaction(
            id = (recurring.maxOfOrNull { it.id } ?: 0L) + 1L,
            profileId = profileId,
            accountId = account.id,
            categoryId = category.id,
            categoryName = category.name,
            categoryIcon = category.icon,
            categoryColor = category.color,
            type = input.type,
            amountCents = input.amountCents,
            currencyCode = account.currencyCode,
            note = input.note,
            frequency = input.frequency,
            nextRunAtEpochMillis = requireNotNull(input.nextRunAtEpochMillis),
            isActive = true,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        recurring += created
        return created
    }

    override suspend fun updateRecurring(
        profileId: Long,
        recurringId: Long,
        input: UpdateRecurringTransactionInput,
    ): RecurringTransaction {
        updatedInputs += input
        val index = recurring.indexOfFirst { it.id == recurringId }
        val existing = recurring[index]
        val account = input.accountId
            ?.let { accountId -> accountsFixture.first { it.id == accountId } }
        val category = input.categoryId
            ?.let { categoryId -> categoriesFixture.first { it.id == categoryId } }
        val updated = existing.copy(
            accountId = input.accountId ?: existing.accountId,
            categoryId = input.categoryId ?: existing.categoryId,
            categoryName = category?.name ?: existing.categoryName,
            categoryIcon = category?.icon ?: existing.categoryIcon,
            categoryColor = category?.color ?: existing.categoryColor,
            type = input.type ?: existing.type,
            amountCents = input.amountCents ?: existing.amountCents,
            currencyCode = account?.currencyCode ?: existing.currencyCode,
            note = input.note ?: existing.note,
            frequency = input.frequency ?: existing.frequency,
            nextRunAtEpochMillis = input.nextRunAtEpochMillis ?: existing.nextRunAtEpochMillis,
        )
        recurring[index] = updated
        return updated
    }

    override suspend fun toggleRecurringActive(profileId: Long, recurringId: Long): RecurringTransaction {
        toggledRecurringIds += recurringId
        val index = recurring.indexOfFirst { it.id == recurringId }
        val updated = recurring[index].copy(isActive = !recurring[index].isActive)
        recurring[index] = updated
        return updated
    }

    override suspend fun deleteRecurring(profileId: Long, recurringId: Long) {
        deletedRecurringIds += recurringId
        recurring.removeAll { it.id == recurringId }
    }

    override suspend fun processDue(nowEpochMillis: Long?): ProcessRecurringDueResult {
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

private fun recurringFixture(
    id: Long,
    note: String,
    categoryName: String,
    type: TransactionType = TransactionType.Expense,
    amountCents: Long,
    nextRunAtEpochMillis: Long,
    isActive: Boolean,
): RecurringTransaction {
    return RecurringTransaction(
        id = id,
        profileId = 1,
        accountId = 1,
        categoryId = id,
        categoryName = categoryName,
        categoryIcon = if (type == TransactionType.Income) "salary" else "home",
        categoryColor = if (type == TransactionType.Income) "#10B981" else "#EF4444",
        type = type,
        amountCents = amountCents,
        currencyCode = "USD",
        note = note,
        frequency = RecurringFrequency.Monthly,
        nextRunAtEpochMillis = nextRunAtEpochMillis,
        isActive = isActive,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}

private val accountsFixture = listOf(
    Account(
        id = 1,
        profileId = 1,
        name = "Main card",
        icon = "wallet",
        color = "#6366F1",
        type = AccountType.Checking,
        currencyCode = "USD",
        isDefault = true,
        includeInTotal = true,
        balanceCents = 125_50,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
)

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Housing",
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

private fun String.toStartOfDayEpochMillis(): Long {
    return LocalDate.parse(this)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}
