package dev.horex.moneytracker.feature.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.accounts.Account
import dev.horex.moneytracker.core.accounts.AccountType
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accountsScreenRendersOnSmallWidth() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 560.dp)) {
                    AccountsScreen(
                        state = AccountsUiState(accounts = accountsFixture),
                        onRetry = {},
                        onAddAccount = {},
                        onEditAccount = {},
                        onDeleteAccount = {},
                        onSetDefault = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Accounts").assertIsDisplayed()
        composeRule.onNodeWithText("Main card").assertIsDisplayed()
        composeRule.onNodeWithText("Pocket cash").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add account").assertIsDisplayed()
    }

    @Test
    fun accountsScreenShowsConstraintError() {
        composeRule.setContent {
            MoneyTrackerTheme {
                AccountsScreen(
                    state = AccountsUiState(
                        accounts = accountsFixture,
                        error = AccountsError.HasTransactions,
                    ),
                    onRetry = {},
                    onAddAccount = {},
                    onEditAccount = {},
                    onDeleteAccount = {},
                    onSetDefault = {},
                )
            }
        }

        composeRule.onNodeWithText("This account has transactions and cannot be deleted.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    private companion object {
        val accountsFixture = listOf(
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
                balanceCents = 125_50,
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
            Account(
                id = 2,
                profileId = 1,
                name = "Pocket cash",
                icon = "cash",
                color = "#F59E0B",
                type = AccountType.Cash,
                currencyCode = "USD",
                isDefault = false,
                includeInTotal = false,
                balanceCents = 42_00,
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
        )
    }
}
