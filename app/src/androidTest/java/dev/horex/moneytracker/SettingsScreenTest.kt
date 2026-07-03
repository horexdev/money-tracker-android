package dev.horex.moneytracker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.preferences.AppThemePreference
import dev.horex.moneytracker.core.currency.SaveExchangeRateOverrideInput
import dev.horex.moneytracker.core.preferences.MoneyTrackerSettings
import dev.horex.moneytracker.core.preferences.SettingsNotificationPreferences
import dev.horex.moneytracker.core.preferences.SettingsUiPreferences
import dev.horex.moneytracker.core.preferences.StatsChartStylePreference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreenRendersOfflineParitySectionsWithoutForbiddenIdentityText() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 960.dp)) {
                    SettingsScreen(
                        state = screenState,
                        onRetry = {},
                        onDismissMessage = {},
                        onSelectProfile = {},
                        onProfileLabelInputChange = {},
                        onSaveProfileLabel = {},
                        onNewProfileLabelChange = {},
                        onCreateProfile = {},
                        onLanguageSelected = {},
                        onThemeSelected = {},
                        onHideAmountsChanged = {},
                        onAnimateNumbersChanged = {},
                        onChartStyleSelected = {},
                        onNotificationChanged = { _, _ -> },
                        onOpenNotificationSettings = {},
                        onDisplayCurrenciesInputChange = {},
                        onSaveDisplayCurrencies = {},
                        onRateBaseChange = {},
                        onRateTargetChange = {},
                        onRateDateChange = {},
                        onRateValueChange = {},
                        onSaveRateOverride = {},
                        onDeleteRateOverride = {},
                        onOpenImportExport = {},
                        onResetRequested = {},
                        onResetDismiss = {},
                        onResetConfirmed = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Local profiles").assertIsDisplayed()
        composeRule.onNodeWithText("Language").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance and privacy").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-screen")
            .performScrollToNode(hasText("Budget alerts"))
        composeRule.onNodeWithText("Budget alerts").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-screen")
            .performScrollToNode(hasText("Currencies and rates"))
        composeRule.onNodeWithText("Currencies and rates").assertIsDisplayed()
        composeRule.onNodeWithText("Rate source").assertIsDisplayed()
        composeRule.onNodeWithText("2026-07-03, source: Saved snapshot, rate 0.93").assertIsDisplayed()
        composeRule.onNodeWithText("Manual rate override").assertIsDisplayed()
        composeRule.onNodeWithText("Manual rate history").assertIsDisplayed()
        composeRule.onNodeWithText("2026-07-03, rate 0.93, source: Manual override").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-screen")
            .performScrollToNode(hasText("Data"))
        composeRule.onNodeWithText("Import / Export").assertIsDisplayed()
        composeRule.onNodeWithText("Reset current profile").assertIsDisplayed()

        forbiddenUiTokens.forEach { token ->
            composeRule.onAllNodesWithText(token, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun resetConfirmationMakesScopeExplicitlyLocal() {
        composeRule.setContent {
            MoneyTrackerTheme {
                SettingsScreen(
                    state = screenState.copy(showResetConfirm = true),
                    onRetry = {},
                    onDismissMessage = {},
                    onSelectProfile = {},
                    onProfileLabelInputChange = {},
                    onSaveProfileLabel = {},
                    onNewProfileLabelChange = {},
                    onCreateProfile = {},
                    onLanguageSelected = {},
                    onThemeSelected = {},
                    onHideAmountsChanged = {},
                    onAnimateNumbersChanged = {},
                    onChartStyleSelected = {},
                    onNotificationChanged = { _, _ -> },
                    onOpenNotificationSettings = {},
                    onDisplayCurrenciesInputChange = {},
                    onSaveDisplayCurrencies = {},
                    onRateBaseChange = {},
                    onRateTargetChange = {},
                    onRateDateChange = {},
                    onRateValueChange = {},
                    onSaveRateOverride = {},
                    onDeleteRateOverride = {},
                    onOpenImportExport = {},
                    onResetRequested = {},
                    onResetDismiss = {},
                    onResetConfirmed = {},
                )
            }
        }

        composeRule.onNodeWithText("Reset current profile?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This deletes the active local profile data on this device. Other local profiles are not reset.",
        ).assertIsDisplayed()
    }

    @Test
    fun notificationStatusExplainsRuntimePermissionBlockedDelivery() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 960.dp)) {
                    SettingsScreen(
                        state = screenState.copy(
                            notificationDeliveryState = SettingsNotificationDeliveryState.RuntimePermissionDenied,
                        ),
                        onRetry = {},
                        onDismissMessage = {},
                        onSelectProfile = {},
                        onProfileLabelInputChange = {},
                        onSaveProfileLabel = {},
                        onNewProfileLabelChange = {},
                        onCreateProfile = {},
                        onLanguageSelected = {},
                        onThemeSelected = {},
                        onHideAmountsChanged = {},
                        onAnimateNumbersChanged = {},
                        onChartStyleSelected = {},
                        onNotificationChanged = { _, _ -> },
                        onOpenNotificationSettings = {},
                        onDisplayCurrenciesInputChange = {},
                        onSaveDisplayCurrencies = {},
                        onRateBaseChange = {},
                        onRateTargetChange = {},
                        onRateDateChange = {},
                        onRateValueChange = {},
                        onSaveRateOverride = {},
                        onDeleteRateOverride = {},
                        onOpenImportExport = {},
                        onResetRequested = {},
                        onResetDismiss = {},
                        onResetConfirmed = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-screen")
            .performScrollToNode(hasText("Permission denied"))
        composeRule.onNodeWithText("Permission denied").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Notification permission was denied. Alerts are saved, but Android will block delivery until notifications are allowed in system settings.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Open system settings").assertIsDisplayed()
        composeRule.onNodeWithText("Budget alerts").assertIsDisplayed()
    }

    @Test
    fun notificationStatusExplainsSystemNotificationsBlockedDelivery() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 960.dp)) {
                    SettingsScreen(
                        state = screenState.copy(
                            notificationDeliveryState = SettingsNotificationDeliveryState.SystemNotificationsDisabled,
                        ),
                        onRetry = {},
                        onDismissMessage = {},
                        onSelectProfile = {},
                        onProfileLabelInputChange = {},
                        onSaveProfileLabel = {},
                        onNewProfileLabelChange = {},
                        onCreateProfile = {},
                        onLanguageSelected = {},
                        onThemeSelected = {},
                        onHideAmountsChanged = {},
                        onAnimateNumbersChanged = {},
                        onChartStyleSelected = {},
                        onNotificationChanged = { _, _ -> },
                        onOpenNotificationSettings = {},
                        onDisplayCurrenciesInputChange = {},
                        onSaveDisplayCurrencies = {},
                        onRateBaseChange = {},
                        onRateTargetChange = {},
                        onRateDateChange = {},
                        onRateValueChange = {},
                        onSaveRateOverride = {},
                        onDeleteRateOverride = {},
                        onOpenImportExport = {},
                        onResetRequested = {},
                        onResetDismiss = {},
                        onResetConfirmed = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-screen")
            .performScrollToNode(hasText("Disabled in system settings"))
        composeRule.onNodeWithText("Disabled in system settings").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Notifications are disabled for this app in system settings. Alerts are saved, but delivery is blocked until they are enabled.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Open system settings").assertIsDisplayed()
    }

    @Test
    fun manualRateOverwriteDialogRequiresExplicitConfirmation() {
        var confirmed = false

        composeRule.setContent {
            MoneyTrackerTheme {
                SettingsScreen(
                    state = screenState.copy(
                        pendingRateOverwrite = SettingsPendingRateOverwriteUi(
                            input = SaveExchangeRateOverrideInput(
                                effectiveDate = "2026-07-03",
                                baseCurrency = "USD",
                                targetCurrency = "EUR",
                                rateE8 = 94_000_000L,
                            ),
                            effectiveDate = "2026-07-03",
                            baseCurrency = "USD",
                            targetCurrency = "EUR",
                            existingRate = "0.93",
                            newRate = "0.94",
                        ),
                    ),
                    onRetry = {},
                    onDismissMessage = {},
                    onSelectProfile = {},
                    onProfileLabelInputChange = {},
                    onSaveProfileLabel = {},
                    onNewProfileLabelChange = {},
                    onCreateProfile = {},
                    onLanguageSelected = {},
                    onThemeSelected = {},
                    onHideAmountsChanged = {},
                    onAnimateNumbersChanged = {},
                    onChartStyleSelected = {},
                    onNotificationChanged = { _, _ -> },
                    onOpenNotificationSettings = {},
                    onDisplayCurrenciesInputChange = {},
                    onSaveDisplayCurrencies = {},
                    onRateBaseChange = {},
                    onRateTargetChange = {},
                    onRateDateChange = {},
                    onRateValueChange = {},
                    onSaveRateOverride = {},
                    onDeleteRateOverride = {},
                    onOpenImportExport = {},
                    onResetRequested = {},
                    onResetDismiss = {},
                    onResetConfirmed = {},
                    onConfirmRateOverwrite = { confirmed = true },
                )
            }
        }

        composeRule.onNodeWithText("Replace manual rate?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "A manual rate already exists for USD -> EUR on 2026-07-03. Current rate: 0.93. New rate: 0.94.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Replace rate").performClick()

        assertTrue(confirmed)
    }

    @Test
    fun settingsScreenSelectsDisplayAndManualRateCurrenciesFromCatalog() {
        var displayCurrenciesInput = ""
        var rateBaseInput = ""
        var rateTargetInput = ""

        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 960.dp)) {
                    SettingsScreen(
                        state = screenState.copy(
                            displayCurrenciesInput = "EUR",
                            rateBaseInput = "USD",
                            rateTargetInput = "EUR",
                        ),
                        onRetry = {},
                        onDismissMessage = {},
                        onSelectProfile = {},
                        onProfileLabelInputChange = {},
                        onSaveProfileLabel = {},
                        onNewProfileLabelChange = {},
                        onCreateProfile = {},
                        onLanguageSelected = {},
                        onThemeSelected = {},
                        onHideAmountsChanged = {},
                        onAnimateNumbersChanged = {},
                        onChartStyleSelected = {},
                        onNotificationChanged = { _, _ -> },
                        onOpenNotificationSettings = {},
                        onDisplayCurrenciesInputChange = { displayCurrenciesInput = it },
                        onSaveDisplayCurrencies = {},
                        onRateBaseChange = { rateBaseInput = it },
                        onRateTargetChange = { rateTargetInput = it },
                        onRateDateChange = {},
                        onRateValueChange = {},
                        onSaveRateOverride = {},
                        onDeleteRateOverride = {},
                        onOpenImportExport = {},
                        onResetRequested = {},
                        onResetDismiss = {},
                        onResetConfirmed = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-screen")
            .performScrollToNode(hasText("Currencies and rates"))
        composeRule.onNodeWithTag("settings-display-currency-add").performClick()
        composeRule.onNodeWithTag("settings-display-currency-search").performTextReplacement("TJS")
        composeRule.onNodeWithTag("settings-display-currency-option-TJS").performClick()
        assertTrue(displayCurrenciesInput == "EUR, TJS")

        composeRule.onNodeWithTag("settings-rate-base").performClick()
        composeRule.onNodeWithTag("settings-rate-base-search").performTextReplacement("Euro")
        composeRule.onNodeWithTag("settings-rate-base-option-EUR").performClick()
        assertTrue(rateBaseInput == "EUR")

        composeRule.onNodeWithTag("settings-rate-target").performClick()
        composeRule.onNodeWithTag("settings-rate-target-search").performTextReplacement("TJS")
        composeRule.onNodeWithTag("settings-rate-target-option-TJS").performClick()
        assertTrue(rateTargetInput == "TJS")
    }

    private companion object {
        val screenState = SettingsUiState(
            settings = MoneyTrackerSettings(
                profileId = 1L,
                baseCurrencyCode = "USD",
                displayCurrencyCodes = listOf("EUR", "TJS"),
                languageCode = "en",
                notificationPreferences = SettingsNotificationPreferences(
                    notifyBudgetAlerts = true,
                    notifyRecurringReminders = true,
                    notifyWeeklySummary = false,
                    notifyGoalMilestones = true,
                ),
                uiPreferences = SettingsUiPreferences(
                    statsChartStyle = StatsChartStylePreference.Donut,
                    animateNumbers = true,
                    theme = AppThemePreference.System,
                    hideAmounts = false,
                ),
            ),
            profiles = listOf(
                SettingsProfileUi(
                    id = 1L,
                    label = "Personal",
                    languageCode = "en",
                    isActive = true,
                ),
                SettingsProfileUi(
                    id = 2L,
                    label = "Household",
                    languageCode = "ru",
                    isActive = false,
                ),
            ),
            latestSnapshotDate = "2026-07-03",
            ratePreview = SettingsRatePreviewUi(
                status = SettingsRatePreviewStatus.Ready,
                resolvedRate = SettingsResolvedRateUi(
                    effectiveDate = "2026-07-03",
                    baseCurrency = "USD",
                    targetCurrency = "EUR",
                    rate = "0.93",
                    source = SettingsRateSourceUi.Snapshot,
                ),
            ),
            manualRateOverrides = listOf(
                SettingsRateOverrideUi(
                    id = 1L,
                    effectiveDate = "2026-07-03",
                    baseCurrency = "USD",
                    targetCurrency = "EUR",
                    rate = "0.93",
                    source = SettingsRateSourceUi.ManualOverride,
                ),
            ),
            activeProfileLabelInput = "Personal",
            newProfileLabel = "",
            displayCurrenciesInput = "EUR, TJS",
            rateBaseInput = "USD",
            rateTargetInput = "EUR",
            rateDateInput = "2026-07-03",
            rateValueInput = "",
        )

        val forbiddenUiTokens = listOf(
            "Tele" + "gram",
            "init" + "Data",
            "legacy" + "_",
            "user" + "name",
        )
    }
}
