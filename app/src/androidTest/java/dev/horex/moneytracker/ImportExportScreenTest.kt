package dev.horex.moneytracker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupValidationCode
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupValidationIssue
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupValidationSeverity
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportExportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importExportScreenRendersSelectionWithoutBackupRefs() {
        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 1400.dp)) {
                    ImportExportScreen(
                        state = screenState,
                        onRetry = {},
                        onToggleExportProfile = { _, _ -> },
                        onSelectAllExportProfiles = {},
                        onClearExportProfiles = {},
                        onExportEncryptedChange = {},
                        onExportPasswordChange = {},
                        onExport = {},
                        onCsvFromDateChange = {},
                        onCsvToDateChange = {},
                        onCsvTypeChange = {},
                        onCsvCurrencyChange = {},
                        onCsvSearchChange = {},
                        onExportCsv = {},
                        onChooseRestoreDocument = {},
                        onRestorePasswordChange = {},
                        onPreviewRestoreDocument = {},
                        onToggleRestoreProfile = { _, _ -> },
                        onRestoreProfileLabelChange = { _, _ -> },
                        onRestore = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Import / Export").assertIsDisplayed()
        composeRule.onNodeWithText("Personal").assertIsDisplayed()
        composeRule.onNodeWithText("Household").assertIsDisplayed()
        composeRule.onAllNodesWithText("profile:1").assertCountEquals(0)
        composeRule.onNodeWithText("Local profile name").assertIsDisplayed()
        composeRule.onNodeWithText("Save backup").assertIsDisplayed()
        composeRule.onNodeWithText("CSV transactions").assertIsDisplayed()
        composeRule.onNodeWithText("Save CSV").assertIsDisplayed()
    }

    @Test
    fun validationErrorHidesForbiddenIdentityPath() {
        val issue = MoneyTrackerBackupValidationIssue(
            severity = MoneyTrackerBackupValidationSeverity.Error,
            code = MoneyTrackerBackupValidationCode.ForbiddenSourceIdentityField,
            path = "\$.profiles[0].telegram_id",
            message = "Forbidden identity",
        )

        composeRule.setContent {
            MoneyTrackerTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 1200.dp)) {
                    ImportExportScreen(
                        state = screenState.copy(
                            restorePreview = RestorePreviewUi(
                                encrypted = false,
                                createdAtEpochMillis = null,
                                profileCount = 0,
                                exchangeRateSnapshots = 0,
                                issues = listOf(issue),
                                profiles = emptyList(),
                            ),
                            selectedRestoreProfileRefs = emptySet(),
                            restoreProfileLabels = emptyMap(),
                        ),
                        onRetry = {},
                        onToggleExportProfile = { _, _ -> },
                        onSelectAllExportProfiles = {},
                        onClearExportProfiles = {},
                        onExportEncryptedChange = {},
                        onExportPasswordChange = {},
                        onExport = {},
                        onCsvFromDateChange = {},
                        onCsvToDateChange = {},
                        onCsvTypeChange = {},
                        onCsvCurrencyChange = {},
                        onCsvSearchChange = {},
                        onExportCsv = {},
                        onChooseRestoreDocument = {},
                        onRestorePasswordChange = {},
                        onPreviewRestoreDocument = {},
                        onToggleRestoreProfile = { _, _ -> },
                        onRestoreProfileLabelChange = { _, _ -> },
                        onRestore = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Backup contains external identity fields and cannot be restored.")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Path: \$.profiles[0].telegram_id").assertCountEquals(0)
        composeRule.onNodeWithText("Restore selected profiles").assertIsNotEnabled()
    }

    private companion object {
        val screenState = ImportExportUiState(
            exportProfiles = listOf(
                ExportProfileUi(
                    id = 1,
                    label = "Personal",
                    languageCode = "en",
                    displayCurrencies = "USD",
                ),
            ),
            selectedExportProfileIds = setOf(1),
            restoreDocumentSelected = true,
            restorePreview = RestorePreviewUi(
                encrypted = false,
                createdAtEpochMillis = 1_720_000_000_000,
                profileCount = 1,
                exchangeRateSnapshots = 2,
                issues = emptyList(),
                profiles = listOf(
                    RestoreProfileUi(
                        ref = "profile:1",
                        backupLabel = "Household",
                        defaultLocalLabel = "Household",
                        counts = RestoreProfileCountsUi(
                            accounts = 2,
                            categories = 8,
                            transactions = 24,
                            transfers = 2,
                            budgets = 1,
                            recurringTransactions = 0,
                            savingsGoals = 1,
                            goalTransactions = 0,
                            exchangeRateOverrides = 0,
                            transactionTemplates = 3,
                        ),
                    ),
                ),
            ),
            selectedRestoreProfileRefs = setOf("profile:1"),
            restoreProfileLabels = mapOf("profile:1" to "Household"),
        )
    }
}
