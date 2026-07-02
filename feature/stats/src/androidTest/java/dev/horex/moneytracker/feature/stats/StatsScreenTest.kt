package dev.horex.moneytracker.feature.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerThemeMode
import dev.horex.moneytracker.core.preferences.MoneyTrackerSettings
import dev.horex.moneytracker.core.preferences.SettingsRepository
import dev.horex.moneytracker.core.preferences.SettingsUiPreferences
import dev.horex.moneytracker.core.preferences.StatsChartStylePreference
import dev.horex.moneytracker.core.preferences.UpdateSettingsInput
import dev.horex.moneytracker.core.stats.CategoryStat
import dev.horex.moneytracker.core.stats.StatsPeriod
import dev.horex.moneytracker.core.stats.StatsQuery
import dev.horex.moneytracker.core.stats.StatsRange
import dev.horex.moneytracker.core.stats.StatsRepository
import dev.horex.moneytracker.core.stats.StatsSnapshot
import dev.horex.moneytracker.core.stats.StatsTransactionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statsScreenRendersLongLabelsOnSmallWidth() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 840.dp)) {
                    StatsScreen(
                        state = StatsUiState(
                            accounts = accountsFixture,
                            settings = settingsFixture,
                            snapshot = snapshotFixture,
                        ),
                        selectedAccountId = 1,
                        selectedPeriod = StatsPeriod.Month,
                        selectedCurrencyCode = "USD",
                        selectedType = StatsTransactionType.Expense,
                        chartStyle = StatsChartStylePreference.Donut,
                        onSelectAccount = {},
                        onSelectPeriod = {},
                        onSelectCurrency = {},
                        onSelectType = {},
                        onSelectChartStyle = {},
                        onOpenHistory = { _, _ -> },
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Stats").assertIsDisplayed()
        composeRule.onNodeWithTag("stats-chart-donut").assertIsDisplayed()
        composeRule.onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-breakdown-row-1"))
        composeRule.onNodeWithText(longCategoryName).assertIsDisplayed()
    }

    @Test
    fun statsRouteLoadsDefaultAccountAndAppliesPeriodFilter() {
        val statsRepository = FakeStatsRepository()

        composeRule.setContent {
            MoneyTrackerTheme {
                StatsRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    accountsRepository = FakeAccountsRepository(),
                    settingsRepository = FakeSettingsRepository(),
                    statsRepository = statsRepository,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            statsRepository.queries.isNotEmpty()
        }
        assertEquals(1L, statsRepository.queries.last().accountId)

        composeRule.onNodeWithTag("stats-period-week").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            statsRepository.queries.lastOrNull()?.period == StatsPeriod.Week
        }

        assertEquals(StatsPeriod.Week, statsRepository.queries.last().period)
    }

    @Test
    fun statsRoutePersistsChartStyleAndShowsProfitChart() {
        val settingsRepository = FakeSettingsRepository()

        composeRule.setContent {
            MoneyTrackerTheme {
                StatsRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    accountsRepository = FakeAccountsRepository(),
                    settingsRepository = settingsRepository,
                    statsRepository = FakeStatsRepository(),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("stats-chart-option-profit_bars").fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("stats-chart-option-profit_bars").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            StatsChartStylePreference.ProfitBars in settingsRepository.updatedStyles
        }

        composeRule.onNodeWithTag("stats-chart-profit_bars").assertIsDisplayed()
    }

    @Test
    fun statsRouteReportsDrilldownCategoryAndRange() {
        var reportedCategoryId: Long? = null
        var reportedRange: StatsRange? = null

        composeRule.setContent {
            MoneyTrackerTheme {
                StatsRoute(
                    localProfileBootstrapper = LocalProfileBootstrapper { profileFixture },
                    accountsRepository = FakeAccountsRepository(),
                    settingsRepository = FakeSettingsRepository(),
                    statsRepository = FakeStatsRepository(),
                    onOpenHistory = { categoryId, range ->
                        reportedCategoryId = categoryId
                        reportedRange = range
                    },
                )
            }
        }

        composeRule.onNodeWithTag("stats-chart-donut").assertIsDisplayed()
        composeRule.onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-breakdown-row-1"))
        composeRule.onNodeWithTag("stats-breakdown-row-1")
            .performClick()

        assertEquals(1L, reportedCategoryId)
        assertEquals(snapshotRange, reportedRange)
    }

    @Test
    fun statsScreenRendersProfitChartInDarkMode() {
        composeRule.setContent {
            MoneyTrackerTheme(themeMode = MoneyTrackerThemeMode.Dark) {
                StatsScreen(
                    state = StatsUiState(
                        accounts = accountsFixture,
                        settings = settingsFixture,
                        snapshot = snapshotFixture,
                    ),
                    selectedAccountId = 1,
                    selectedPeriod = StatsPeriod.Month,
                    selectedCurrencyCode = "USD",
                    selectedType = StatsTransactionType.Expense,
                    chartStyle = StatsChartStylePreference.ProfitBars,
                    onSelectAccount = {},
                    onSelectPeriod = {},
                    onSelectCurrency = {},
                    onSelectType = {},
                    onSelectChartStyle = {},
                    onOpenHistory = { _, _ -> },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("stats-chart-profit_bars").assertIsDisplayed()
    }
}

private class FakeStatsRepository : StatsRepository {
    val queries = mutableListOf<StatsQuery>()

    override suspend fun getStats(profileId: Long, query: StatsQuery): StatsSnapshot {
        queries += query
        return snapshotFixture.copy(
            profileId = profileId,
            period = query.period.storageValue,
        )
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private var settings = settingsFixture
    val updatedStyles = mutableListOf<StatsChartStylePreference>()

    override suspend fun getSettings(): MoneyTrackerSettings {
        return settings
    }

    override suspend fun updateSettings(input: UpdateSettingsInput): MoneyTrackerSettings {
        val style = input.uiPreferences?.statsChartStyle
        if (style != null) {
            updatedStyles += style
            settings = settings.copy(
                uiPreferences = settings.uiPreferences.copy(statsChartStyle = style),
            )
        }
        return settings
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

private val settingsFixture = MoneyTrackerSettings(
    profileId = 1,
    baseCurrencyCode = "USD",
    uiPreferences = SettingsUiPreferences(
        statsChartStyle = StatsChartStylePreference.Donut,
    ),
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
        name = "Cash reserve with a longer name",
        icon = "cash",
        color = "#F59E0B",
        type = AccountType.Cash,
        currencyCode = "EUR",
        isDefault = false,
        includeInTotal = true,
        balanceCents = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
)

private const val longCategoryName = "Very long household and family subscription category label"

private val snapshotRange = StatsRange(
    fromEpochMillisInclusive = 1_782_864_000_000L,
    toEpochMillisExclusive = 1_785_542_400_000L,
)

private val snapshotFixture = StatsSnapshot(
    profileId = 1,
    period = "month",
    range = snapshotRange,
    items = listOf(
        CategoryStat(
            categoryId = 1,
            categoryName = longCategoryName,
            categoryIcon = "home",
            categoryColor = "#EF4444",
            type = StatsTransactionType.Expense,
            totalCents = 42_500,
            transactionCount = 7,
            currencyCode = "USD",
        ),
        CategoryStat(
            categoryId = 2,
            categoryName = "Groceries",
            categoryIcon = "cart",
            categoryColor = "#F59E0B",
            type = StatsTransactionType.Expense,
            totalCents = 20_250,
            transactionCount = 5,
            currencyCode = "USD",
        ),
        CategoryStat(
            categoryId = 3,
            categoryName = "Salary",
            categoryIcon = "salary",
            categoryColor = "#10B981",
            type = StatsTransactionType.Income,
            totalCents = 280_000,
            transactionCount = 1,
            currencyCode = "USD",
        ),
        CategoryStat(
            categoryId = 4,
            categoryName = "Travel",
            categoryIcon = "plane",
            categoryColor = "#0EA5E9",
            type = StatsTransactionType.Expense,
            totalCents = 9_000,
            transactionCount = 1,
            currencyCode = "EUR",
        ),
    ),
)
