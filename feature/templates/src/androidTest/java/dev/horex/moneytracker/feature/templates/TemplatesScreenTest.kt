package dev.horex.moneytracker.feature.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.categories.CreateCategoryInput
import dev.horex.moneytracker.core.categories.UpdateCategoryInput
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.templates.ApplyTransactionTemplateInput
import dev.horex.moneytracker.core.templates.CreateTransactionTemplateInput
import dev.horex.moneytracker.core.templates.TransactionTemplate
import dev.horex.moneytracker.core.templates.TransactionTemplateAmountMode
import dev.horex.moneytracker.core.templates.TransactionTemplatesRepository
import dev.horex.moneytracker.core.templates.UpdateTransactionTemplateInput
import dev.horex.moneytracker.core.transactions.MoneyTransaction
import dev.horex.moneytracker.core.transactions.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplatesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun templatesRouteAppliesFixedTemplateImmediately() {
        val templatesRepository = FakeTransactionTemplatesRepository(
            initialTemplates = listOf(
                templateFixture(id = 1, name = "Lunch", amountMode = TransactionTemplateAmountMode.Fixed),
            ),
        )

        composeRule.setContent {
            MoneyTrackerTheme {
                TemplatesRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    templatesRepository = templatesRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                    nowProvider = { CREATED_AT },
                )
            }
        }

        composeRule.onNodeWithText("Lunch").assertIsDisplayed()
        composeRule.onNodeWithTag("template-apply-1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            templatesRepository.appliedTemplates.isNotEmpty()
        }

        val applied = templatesRepository.appliedTemplates.single()
        assertEquals(1L, applied.templateId)
        assertEquals(null, applied.input.variableAmountCents)
        assertEquals(CREATED_AT, applied.input.createdAtEpochMillis)
    }

    @Test
    fun templatesRoutePromptsForVariableTemplateAmount() {
        val templatesRepository = FakeTransactionTemplatesRepository(
            initialTemplates = listOf(
                templateFixture(
                    id = 2,
                    name = "Taxi",
                    amountMode = TransactionTemplateAmountMode.Variable,
                ),
            ),
        )

        composeRule.setContent {
            MoneyTrackerTheme {
                TemplatesRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    templatesRepository = templatesRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                    nowProvider = { CREATED_AT },
                )
            }
        }

        composeRule.onNodeWithTag("template-apply-2").performClick()
        composeRule.onNodeWithText("Enter amount").assertIsDisplayed()
        composeRule.onNodeWithTag("template-variable-amount").performTextReplacement("22.75")
        composeRule.onNodeWithTag("template-variable-apply").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            templatesRepository.appliedTemplates.isNotEmpty()
        }

        val applied = templatesRepository.appliedTemplates.single()
        assertEquals(2L, applied.templateId)
        assertEquals(2_275L, applied.input.variableAmountCents)
    }

    @Test
    fun templatesRouteReordersTemplatesWithFullIdList() {
        val templatesRepository = FakeTransactionTemplatesRepository(
            initialTemplates = listOf(
                templateFixture(id = 1, name = "Lunch", amountMode = TransactionTemplateAmountMode.Fixed),
                templateFixture(id = 2, name = "Taxi", amountMode = TransactionTemplateAmountMode.Variable),
            ),
        )

        composeRule.setContent {
            MoneyTrackerTheme {
                TemplatesRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    templatesRepository = templatesRepository,
                    accountsRepository = FakeAccountsRepository(),
                    categoriesRepository = FakeCategoriesRepository(),
                )
            }
        }

        composeRule.onNodeWithTag("template-move-down-1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            templatesRepository.reorderRequests.isNotEmpty()
        }

        assertEquals(listOf(2L, 1L), templatesRepository.reorderRequests.single())
        assertEquals(listOf(2L, 1L), templatesRepository.currentTemplates.map { it.id })
    }
}

private data class AppliedTemplate(
    val templateId: Long,
    val input: ApplyTransactionTemplateInput,
)

private class FakeTransactionTemplatesRepository(
    initialTemplates: List<TransactionTemplate>,
) : TransactionTemplatesRepository {
    private val templates = initialTemplates.toMutableList()
    val appliedTemplates = mutableListOf<AppliedTemplate>()
    val reorderRequests = mutableListOf<List<Long>>()
    val currentTemplates: List<TransactionTemplate>
        get() = templates.toList()

    override suspend fun listTemplates(profileId: Long): List<TransactionTemplate> {
        return templates.toList()
    }

    override suspend fun getTemplate(profileId: Long, templateId: Long): TransactionTemplate {
        return templates.first { it.id == templateId }
    }

    override suspend fun createTemplate(
        profileId: Long,
        input: CreateTransactionTemplateInput,
    ): TransactionTemplate {
        throw UnsupportedOperationException()
    }

    override suspend fun updateTemplate(
        profileId: Long,
        templateId: Long,
        input: UpdateTransactionTemplateInput,
    ): TransactionTemplate {
        throw UnsupportedOperationException()
    }

    override suspend fun deleteTemplate(profileId: Long, templateId: Long) {
        templates.removeAll { it.id == templateId }
    }

    override suspend fun reorderTemplates(
        profileId: Long,
        orderedTemplateIds: List<Long>,
    ): List<TransactionTemplate> {
        reorderRequests += orderedTemplateIds
        val byId = templates.associateBy { it.id }
        templates.clear()
        templates += orderedTemplateIds.mapIndexed { index, id ->
            requireNotNull(byId[id]).copy(sortOrder = index)
        }
        return templates.toList()
    }

    override suspend fun applyTemplate(
        profileId: Long,
        templateId: Long,
        input: ApplyTransactionTemplateInput,
    ): MoneyTransaction {
        appliedTemplates += AppliedTemplate(templateId, input)
        val template = templates.first { it.id == templateId }
        return MoneyTransaction(
            id = appliedTemplates.size.toLong(),
            profileId = profileId,
            type = template.type,
            amountCents = input.variableAmountCents ?: template.amountCents,
            categoryId = template.categoryId,
            categoryName = template.categoryName,
            categoryIcon = template.categoryIcon,
            categoryColor = template.categoryColor,
            accountId = template.accountId,
            accountName = template.accountName,
            note = template.note,
            currencyCode = template.currencyCode,
            snapshotDate = "2026-07-03",
            createdAtEpochMillis = input.createdAtEpochMillis ?: CREATED_AT,
            isAdjustment = false,
        )
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

private fun templateFixture(
    id: Long,
    name: String,
    amountMode: TransactionTemplateAmountMode,
): TransactionTemplate {
    return TransactionTemplate(
        id = id,
        profileId = 1,
        name = name,
        type = TransactionType.Expense,
        amountCents = 1_250,
        amountMode = amountMode,
        categoryId = 1,
        categoryName = "Food",
        categoryIcon = "shopping-bag",
        categoryColor = "#EF4444",
        accountId = 1,
        accountName = "Main card",
        currencyCode = "USD",
        note = name,
        sortOrder = id.toInt() - 1,
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
        name = "Food",
        icon = "shopping-bag",
        type = CategoryType.Expense,
        color = "#EF4444",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
)

private const val CREATED_AT = 1_783_036_800_000L
