package dev.horex.moneytracker.feature.categories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.categories.CreateCategoryInput
import dev.horex.moneytracker.core.categories.UpdateCategoryInput
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoriesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun categoriesScreenRendersOnSmallWidthAndDisablesProtectedActions() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 640.dp)) {
                    CategoriesScreen(
                        state = CategoriesUiState(categories = categoriesFixture),
                        selectedFilter = CategoryTypeFilter.All,
                        onSelectFilter = {},
                        onRetry = {},
                        onAddCategory = {},
                        onEditCategory = {},
                        onDeleteCategory = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Categories").assertIsDisplayed()
        composeRule.onNodeWithText("Food").assertIsDisplayed()
        composeRule.onNodeWithTag("category-card-3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Protected").assertIsDisplayed()
        composeRule.onNodeWithTag("category-edit-3").assertIsNotEnabled()
        composeRule.onNodeWithTag("category-delete-3").assertIsNotEnabled()
    }

    @Test
    fun categoriesRouteAppliesTypeFilter() {
        val repository = FakeCategoriesRepository()

        composeRule.setContent {
            MoneyTrackerTheme {
                CategoriesRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    categoriesRepository = repository,
                )
            }
        }

        composeRule.onNodeWithText("Food").assertIsDisplayed()
        composeRule.onNodeWithTag("categories-filter-Income").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.lastListType == CategoryType.Income
        }
        assertEquals(CategoryType.Income, repository.lastListType)
        composeRule.onNodeWithText("Salary").assertIsDisplayed()
    }

    @Test
    fun categoriesRouteValidatesDuplicateNamesBeforeCreate() {
        composeRule.setContent {
            MoneyTrackerTheme {
                CategoriesRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    categoriesRepository = FakeCategoriesRepository(),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add category").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Food")

        composeRule.onNodeWithText("A category with this name already exists.").assertIsDisplayed()
        composeRule.onNodeWithText("Create category").assertIsNotEnabled()
    }

    @Test
    fun categoriesRouteCreatesCategoryFromSheet() {
        val repository = FakeCategoriesRepository()

        composeRule.setContent {
            MoneyTrackerTheme {
                CategoriesRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    categoriesRepository = repository,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add category").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Bonus")
        composeRule.onNodeWithTag("categories-form-type-Income").performClick()
        composeRule.onNodeWithText("Create category").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.lastCreateInput?.name == "Bonus"
        }
        assertEquals(CategoryType.Income, repository.lastCreateInput?.type)
    }
}

private class FakeCategoriesRepository : CategoriesRepository {
    private var categories = categoriesFixture.toMutableList()
    var lastListType: CategoryType? = null
        private set
    var lastCreateInput: CreateCategoryInput? = null
        private set

    override suspend fun listCategories(
        profileId: Long,
        type: CategoryType?,
        sortOrder: CategorySortOrder,
    ): List<Category> {
        return listAllCategories(profileId, type, sortOrder).filterNot { it.isProtected }
    }

    override suspend fun listAllCategories(
        profileId: Long,
        type: CategoryType?,
        sortOrder: CategorySortOrder,
    ): List<Category> {
        lastListType = type
        return categories
            .filter { it.matches(type) }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun getCategory(profileId: Long, categoryId: Long): Category {
        return categories.first { it.id == categoryId }
    }

    override suspend fun getProtectedCategoryByType(profileId: Long, type: CategoryType): Category {
        return categories.first { it.type == type && it.isProtected }
    }

    override suspend fun hasEditableCategories(profileId: Long): Boolean {
        return categories.any { !it.isProtected }
    }

    override suspend fun createCategory(profileId: Long, input: CreateCategoryInput): Category {
        lastCreateInput = input
        val category = Category(
            id = (categories.maxOfOrNull { it.id } ?: 0L) + 1L,
            profileId = profileId,
            name = input.name,
            icon = input.icon,
            type = input.type,
            color = input.color,
            isProtected = false,
            updatedAtEpochMillis = 2,
            deletedAtEpochMillis = null,
        )
        categories.add(category)
        return category
    }

    override suspend fun updateCategory(profileId: Long, categoryId: Long, input: UpdateCategoryInput): Category {
        val existing = categories.first { it.id == categoryId }
        val updated = existing.copy(
            name = input.name ?: existing.name,
            icon = input.icon ?: existing.icon,
            type = input.type ?: existing.type,
            color = input.color ?: existing.color,
        )
        categories = categories.map { if (it.id == categoryId) updated else it }.toMutableList()
        return updated
    }

    override suspend fun deleteCategory(profileId: Long, categoryId: Long) {
        categories = categories.filterNot { it.id == categoryId }.toMutableList()
    }

    private fun Category.matches(type: CategoryType?): Boolean {
        return type == null ||
            this.type == type ||
            (type in setOf(CategoryType.Expense, CategoryType.Income) && this.type == CategoryType.Both)
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
    statsChartStyle = "donut",
    animateNumbers = null,
    theme = "system",
    hideAmounts = false,
    createdAtEpochMillis = 1,
    updatedAtEpochMillis = 1,
)

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Food",
        icon = "fork-knife",
        type = CategoryType.Expense,
        color = "#f97316",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
    Category(
        id = 2,
        profileId = 1,
        name = "Salary",
        icon = "briefcase",
        type = CategoryType.Income,
        color = "#10b981",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
    Category(
        id = 3,
        profileId = 1,
        name = "Transfer",
        icon = "arrows-left-right",
        type = CategoryType.Transfer,
        color = "#6366f1",
        isProtected = true,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
)
