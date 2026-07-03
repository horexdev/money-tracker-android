package dev.horex.moneytracker.feature.budgets

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
import dev.horex.moneytracker.core.budgets.Budget
import dev.horex.moneytracker.core.budgets.BudgetPeriod
import dev.horex.moneytracker.core.budgets.BudgetTransaction
import dev.horex.moneytracker.core.budgets.BudgetsRepository
import dev.horex.moneytracker.core.budgets.CreateBudgetInput
import dev.horex.moneytracker.core.budgets.UpdateBudgetInput
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.categories.CreateCategoryInput
import dev.horex.moneytracker.core.categories.UpdateCategoryInput
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun budgetsScreenRendersOverLimitStateAndNotificationToggleOnSmallWidth() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 760.dp)) {
                    BudgetsScreen(
                        state = BudgetsUiState(
                            budgets = listOf(
                                budgetFixture(
                                    id = 1,
                                    categoryName = "Groceries",
                                    spentCents = 42_500,
                                    limitCents = 60_000,
                                    notificationsEnabled = true,
                                ),
                                budgetFixture(
                                    id = 2,
                                    categoryName = "Restaurants",
                                    spentCents = 18_250,
                                    limitCents = 15_000,
                                    notificationsEnabled = false,
                                ),
                            ),
                            categories = categoriesFixture,
                        ),
                        onRetry = {},
                        onAddBudget = {},
                        onEditBudget = {},
                        onDeleteBudget = {},
                        onToggleNotifications = { _, _ -> },
                        onOpenTransactions = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Budgets").assertIsDisplayed()
        composeRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeRule.onNodeWithTag("budgets-list")
            .performScrollToNode(hasTestTag("budget-card-2"))
        composeRule.onNodeWithText("Restaurants").assertIsDisplayed()
        composeRule.onNodeWithText("Over limit").assertIsDisplayed()
        composeRule.onNodeWithText("Over limit by 3.25 USD").assertIsDisplayed()
        composeRule.onNodeWithText("Notifications off - 80%").assertIsDisplayed()
    }

    @Test
    fun budgetsRouteCreatesBudgetWithNotificationSettings() {
        val budgetsRepository = FakeBudgetsRepository()

        composeRule.setContent {
            MoneyTrackerTheme {
                BudgetsRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    budgetsRepository = budgetsRepository,
                    categoriesRepository = FakeCategoriesRepository(),
                )
            }
        }

        composeRule.onNodeWithText("No budgets yet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add budget").performClick()
        composeRule.onNodeWithTag("budget-limit").performTextReplacement("100.00")
        composeRule.onNodeWithTag("budget-currency").performTextReplacement("EUR")
        composeRule.onNodeWithTag("budget-notify-percent").performTextReplacement("75")
        composeRule.onNodeWithTag("budget-form-notifications").performClick()
        composeRule.onNodeWithText("Create budget").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            budgetsRepository.createdInputs.isNotEmpty()
        }

        val input = budgetsRepository.createdInputs.single()
        assertEquals(1L, input.categoryId)
        assertEquals(10_000L, input.limitCents)
        assertEquals(BudgetPeriod.Monthly, input.period)
        assertEquals("EUR", input.currencyCode)
        assertEquals(75, input.notifyAtPercent)
        assertFalse(input.notificationsEnabled)
    }

    @Test
    fun budgetsRouteUpdatesTogglesShowsTransactionsAndDeletesBudget() {
        val budgetsRepository = FakeBudgetsRepository(
            initialBudgets = listOf(
                budgetFixture(
                    id = 1,
                    categoryName = "Groceries",
                    spentCents = 42_500,
                    limitCents = 75_000,
                    notificationsEnabled = true,
                ),
            ),
            transactions = listOf(transactionFixture),
        )

        composeRule.setContent {
            MoneyTrackerTheme {
                BudgetsRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    budgetsRepository = budgetsRepository,
                    categoriesRepository = FakeCategoriesRepository(),
                )
            }
        }

        composeRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeRule.onNodeWithTag("budget-notifications-1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            budgetsRepository.updatedInputs.any { it.notificationsEnabled == false }
        }

        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            budgetsRepository.transactionRequests == 1
        }
        composeRule.onNodeWithText("Weekly market").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cancel").performClick()

        composeRule.onNodeWithContentDescription("Edit budget").performClick()
        composeRule.onNodeWithTag("budget-limit").performTextReplacement("90.00")
        composeRule.onNodeWithText("Save budget").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            budgetsRepository.updatedInputs.any { it.limitCents == 9_000L }
        }

        composeRule.onNodeWithContentDescription("Delete budget").performClick()
        composeRule.onNodeWithText("Delete budget").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            budgetsRepository.deletedBudgetIds == listOf(1L)
        }
    }
}

private class FakeBudgetsRepository(
    initialBudgets: List<Budget> = emptyList(),
    private val transactions: List<BudgetTransaction> = emptyList(),
) : BudgetsRepository {
    private val budgets = initialBudgets.toMutableList()
    val createdInputs = mutableListOf<CreateBudgetInput>()
    val updatedInputs = mutableListOf<UpdateBudgetInput>()
    val deletedBudgetIds = mutableListOf<Long>()
    var transactionRequests = 0
        private set

    override suspend fun listBudgets(profileId: Long): List<Budget> {
        return budgets.toList()
    }

    override suspend fun getBudget(profileId: Long, budgetId: Long): Budget {
        return budgets.first { it.id == budgetId }
    }

    override suspend fun createBudget(profileId: Long, input: CreateBudgetInput): Budget {
        createdInputs += input
        val category = categoriesFixture.first { it.id == input.categoryId }
        val budget = Budget(
            id = (budgets.maxOfOrNull { it.id } ?: 0L) + 1L,
            profileId = profileId,
            categoryId = category.id,
            categoryName = category.name,
            categoryIcon = category.icon,
            categoryColor = category.color,
            limitCents = input.limitCents,
            spentCents = 0,
            period = input.period,
            currencyCode = input.currencyCode,
            notifyAtPercent = input.notifyAtPercent,
            notificationsEnabled = input.notificationsEnabled,
            lastNotifiedPercent = 0,
            lastNotifiedAtEpochMillis = null,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        budgets += budget
        return budget
    }

    override suspend fun updateBudget(
        profileId: Long,
        budgetId: Long,
        input: UpdateBudgetInput,
    ): Budget {
        updatedInputs += input
        val index = budgets.indexOfFirst { it.id == budgetId }
        val existing = budgets[index]
        val updated = existing.copy(
            limitCents = input.limitCents ?: existing.limitCents,
            period = input.period ?: existing.period,
            notifyAtPercent = input.notifyAtPercent ?: existing.notifyAtPercent,
            notificationsEnabled = input.notificationsEnabled ?: existing.notificationsEnabled,
        )
        budgets[index] = updated
        return updated
    }

    override suspend fun deleteBudget(profileId: Long, budgetId: Long) {
        deletedBudgetIds += budgetId
        budgets.removeAll { it.id == budgetId }
    }

    override suspend fun listBudgetTransactions(profileId: Long, budgetId: Long): List<BudgetTransaction> {
        transactionRequests += 1
        return transactions
    }

    override suspend fun recordBudgetThresholdNotification(
        profileId: Long,
        budgetId: Long,
        thresholdPercent: Int,
        periodStartEpochMillis: Long,
        notifiedAtEpochMillis: Long,
    ): Boolean {
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
            type == null || category.type == type || (type == CategoryType.Expense && category.type == CategoryType.Both)
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

private fun budgetFixture(
    id: Long,
    categoryName: String,
    spentCents: Long,
    limitCents: Long,
    notificationsEnabled: Boolean,
): Budget {
    return Budget(
        id = id,
        profileId = 1,
        categoryId = id,
        categoryName = categoryName,
        categoryIcon = "tag",
        categoryColor = if (id == 1L) "#F97316" else "#EF4444",
        limitCents = limitCents,
        spentCents = spentCents,
        period = BudgetPeriod.Monthly,
        currencyCode = "USD",
        notifyAtPercent = 80,
        notificationsEnabled = notificationsEnabled,
        lastNotifiedPercent = 0,
        lastNotifiedAtEpochMillis = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Groceries",
        icon = "shopping-bag",
        type = CategoryType.Expense,
        color = "#F97316",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
    Category(
        id = 2,
        profileId = 1,
        name = "Restaurants",
        icon = "fork-knife",
        type = CategoryType.Expense,
        color = "#EF4444",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
)

private val transactionFixture = BudgetTransaction(
    id = 1,
    profileId = 1,
    amountCents = 4_250,
    categoryId = 1,
    categoryName = "Groceries",
    categoryIcon = "shopping-bag",
    categoryColor = "#F97316",
    accountId = 1,
    accountName = "Main card",
    note = "Weekly market",
    currencyCode = "USD",
    snapshotDate = "2026-07-02",
    createdAtEpochMillis = 1,
)
