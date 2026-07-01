package dev.horex.moneytracker

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.testing.MoneyTrackerTestFixtures
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}
