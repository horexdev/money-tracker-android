package dev.horex.moneytracker

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.testing.MoneyTrackerTestFixtures
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class MoneyTrackerAppComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appShellShowsTopLevelNavigation() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeRule.setContent {
            MoneyTrackerApp()
        }

        composeRule.onNodeWithText(context.getString(R.string.tab_home)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_history)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_add)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_stats)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_more)).assertIsDisplayed()

        assertTrue(MoneyTrackerTestFixtures.cashAccount.localId > 0L)
    }

    @Test
    fun moreRouteLinksToAccountsRoute() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeRule.setContent {
            MoneyTrackerApp()
        }

        composeRule.onNodeWithText(context.getString(R.string.tab_more)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.accounts_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.accounts_more_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.accounts_title)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.accounts_placeholder_subtitle)).assertIsDisplayed()
    }

    @Test
    fun moreRouteLinksToBudgetsRoute() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeRule.setContent {
            MoneyTrackerApp()
        }

        composeRule.onNodeWithText(context.getString(R.string.tab_more)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.budgets_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.budgets_more_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.budgets_title)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.budgets_placeholder_subtitle)).assertIsDisplayed()
    }

    @Test
    fun appBootstrapsLocalProfile() {
        val bootstrapCalls = AtomicInteger(0)
        val bootstrapper = LocalProfileBootstrapper {
            bootstrapCalls.incrementAndGet()
            LocalProfile(
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
        }

        composeRule.setContent {
            MoneyTrackerApp(localProfileBootstrapper = bootstrapper)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            bootstrapCalls.get() == 1
        }
    }
}
