package dev.horex.moneytracker.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerBackupV1ValidationTest {
    @Test
    fun validBackupDryRunIsDeterministicAndClean() {
        val backup = sampleBackup()

        val first = MoneyTrackerBackupV1Validator.validate(backup)
        val second = MoneyTrackerBackupV1Validator.validate(backup)

        assertEquals(first, second)
        assertTrue(first.canImport)
        assertTrue(first.issues.isEmpty())
    }

    @Test
    fun emptyBackupReturnsWarningWithoutBlockingImport() {
        val result = MoneyTrackerBackupV1Validator.validate(
            MoneyTrackerBackup(createdAtEpochMillis = 1_788_200_000_000),
        )

        assertTrue(result.canImport)
        assertTrue(result.errors.isEmpty())
        assertEquals(
            listOf(MoneyTrackerBackupValidationCode.EmptyBackup),
            result.warnings.map { it.code },
        )
        assertEquals("$.profiles", result.warnings.single().path)
    }

    @Test
    fun sourceIdentityFieldsInRawJsonBlockImportBeforeRestore() {
        val encoded = MoneyTrackerBackupV1Json.encodeToString(sampleBackup())
        val withSourceIdentity = encoded
            .replace(
                """"created_at_epoch_millis": 1788200000000,""",
                """"created_at_epoch_millis": 1788200000000,
  "telegram_id": 123456,""",
            )
            .replace(
                """"language_code": "en",""",
                """"language_code": "en",
      "user_id": 123456,
      "username": "source_user",
      "initData": "raw-init-data",""",
            )

        val first = MoneyTrackerBackupV1Validator.validateJson(withSourceIdentity)
        val second = MoneyTrackerBackupV1Validator.validateJson(withSourceIdentity)

        assertEquals(first, second)
        assertFalse(first.canImport)
        assertTrue(
            first.errors.any {
                it.code == MoneyTrackerBackupValidationCode.ForbiddenSourceIdentityField &&
                    it.path == "$.telegram_id"
            },
        )
        assertTrue(
            first.errors.any {
                it.code == MoneyTrackerBackupValidationCode.ForbiddenSourceIdentityField &&
                    it.path == "$.profiles[0].user_id"
            },
        )
        assertTrue(
            first.errors.any {
                it.code == MoneyTrackerBackupValidationCode.ForbiddenSourceIdentityField &&
                    it.path == "$.profiles[0].initData"
            },
        )
    }

    @Test
    fun unresolvedReferencesAreReportedAsDeterministicErrors() {
        val profile = sampleProfile().copy(
            transactions = listOf(
                sampleTransaction(
                    accountRef = "account:missing",
                    categoryRef = "category:missing",
                ),
            ),
            transfers = listOf(
                sampleTransfer(
                    fromAccountRef = "account:missing",
                    fromTransactionRef = "transaction:missing",
                ),
            ),
            budgets = listOf(sampleBudget(categoryRef = "category:missing")),
            recurringTransactions = listOf(
                sampleRecurring(
                    accountRef = "account:missing",
                    categoryRef = "category:missing",
                ),
            ),
            savingsGoals = listOf(sampleSavingsGoal(accountRef = "account:missing")),
            goalTransactions = listOf(sampleGoalTransaction(goalRef = "goal:missing")),
            transactionTemplates = listOf(
                sampleTemplate(
                    accountRef = "account:missing",
                    categoryRef = "category:missing",
                ),
            ),
        )

        val result = MoneyTrackerBackupV1Validator.validate(
            MoneyTrackerBackup(
                createdAtEpochMillis = 1_788_200_000_000,
                profiles = listOf(profile),
            ),
        )

        assertFalse(result.canImport)
        assertEquals(
            listOf(
                "$.profiles[0].budgets[0].category_ref",
                "$.profiles[0].goal_transactions[0].goal_ref",
                "$.profiles[0].recurring_transactions[0].account_ref",
                "$.profiles[0].recurring_transactions[0].category_ref",
                "$.profiles[0].savings_goals[0].account_ref",
                "$.profiles[0].transaction_templates[0].account_ref",
                "$.profiles[0].transaction_templates[0].category_ref",
                "$.profiles[0].transactions[0].account_ref",
                "$.profiles[0].transactions[0].category_ref",
                "$.profiles[0].transfers[0].from_account_ref",
                "$.profiles[0].transfers[0].from_transaction_ref",
            ),
            result.errors
                .filter { it.code == MoneyTrackerBackupValidationCode.UnresolvedRef }
                .map { it.path },
        )
    }

    private fun sampleBackup(): MoneyTrackerBackup {
        return MoneyTrackerBackup(
            createdAtEpochMillis = 1_788_200_000_000,
            profiles = listOf(sampleProfile()),
        )
    }

    private fun sampleProfile(): BackupProfile {
        return BackupProfile(
            ref = "profile:main",
            label = "Offline profile",
            languageCode = "en",
            displayCurrencyCodes = listOf("USD", "EUR"),
            createdAtEpochMillis = 1_788_200_000_000,
            updatedAtEpochMillis = 1_788_200_000_000,
            accounts = listOf(sampleAccount("account:main"), sampleAccount("account:savings")),
            categories = listOf(sampleCategory("category:food"), sampleCategory("category:transfer")),
            transactions = listOf(sampleTransaction()),
            transfers = listOf(sampleTransfer()),
            budgets = listOf(sampleBudget()),
            recurringTransactions = listOf(sampleRecurring()),
            savingsGoals = listOf(sampleSavingsGoal()),
            goalTransactions = listOf(sampleGoalTransaction()),
            transactionTemplates = listOf(sampleTemplate()),
        )
    }

    private fun sampleAccount(ref: String): BackupAccount {
        return BackupAccount(
            ref = ref,
            name = "Main",
            icon = "wallet",
            color = "#6366f1",
            type = BackupAccountType.Checking,
            currencyCode = "USD",
            isDefault = ref == "account:main",
            includeInTotal = true,
            createdAtEpochMillis = 1_788_200_000_000,
            updatedAtEpochMillis = 1_788_200_000_000,
        )
    }

    private fun sampleCategory(ref: String): BackupCategory {
        return BackupCategory(
            ref = ref,
            name = "Food",
            icon = "fork-knife",
            type = BackupCategoryType.Expense,
            color = "#ef4444",
            isProtected = false,
            updatedAtEpochMillis = 1_788_200_000_000,
        )
    }

    private fun sampleTransaction(
        accountRef: String = "account:main",
        categoryRef: String = "category:food",
    ): BackupTransaction {
        return BackupTransaction(
            ref = "transaction:food",
            type = BackupTransactionType.Expense,
            amountCents = 1_250,
            categoryRef = categoryRef,
            accountRef = accountRef,
            currencyCode = "USD",
            snapshotDate = "2026-07-01",
            createdAtEpochMillis = 1_788_200_000_000,
        )
    }

    private fun sampleTransfer(
        fromAccountRef: String = "account:main",
        fromTransactionRef: String = "transaction:food",
    ): BackupTransfer {
        return BackupTransfer(
            ref = "transfer:main",
            fromAccountRef = fromAccountRef,
            toAccountRef = "account:savings",
            amountCents = 2_000,
            fromCurrencyCode = "USD",
            toCurrencyCode = "USD",
            exchangeRateE8 = 100_000_000,
            fromTransactionRef = fromTransactionRef,
            createdAtEpochMillis = 1_788_200_000_000,
        )
    }

    private fun sampleBudget(categoryRef: String = "category:food"): BackupBudget {
        return BackupBudget(
            ref = "budget:food",
            categoryRef = categoryRef,
            limitCents = 50_000,
            period = BackupBudgetPeriod.Monthly,
            currencyCode = "USD",
            notifyAtPercent = 80,
            notificationsEnabled = true,
            lastNotifiedPercent = 0,
            createdAtEpochMillis = 1_788_200_000_000,
            updatedAtEpochMillis = 1_788_200_000_000,
        )
    }

    private fun sampleRecurring(
        accountRef: String = "account:main",
        categoryRef: String = "category:food",
    ): BackupRecurringTransaction {
        return BackupRecurringTransaction(
            ref = "recurring:rent",
            accountRef = accountRef,
            categoryRef = categoryRef,
            type = BackupTransactionType.Expense,
            amountCents = 90_000,
            currencyCode = "USD",
            frequency = BackupRecurringFrequency.Monthly,
            nextRunAtEpochMillis = 1_790_792_000_000,
            isActive = true,
            createdAtEpochMillis = 1_788_200_000_000,
            updatedAtEpochMillis = 1_788_200_000_000,
        )
    }

    private fun sampleSavingsGoal(accountRef: String = "account:savings"): BackupSavingsGoal {
        return BackupSavingsGoal(
            ref = "goal:trip",
            name = "Trip",
            targetCents = 200_000,
            currentCents = 10_000,
            currencyCode = "USD",
            accountRef = accountRef,
            createdAtEpochMillis = 1_788_200_000_000,
            updatedAtEpochMillis = 1_788_200_000_000,
        )
    }

    private fun sampleGoalTransaction(goalRef: String = "goal:trip"): BackupGoalTransaction {
        return BackupGoalTransaction(
            ref = "goal-transaction:trip",
            goalRef = goalRef,
            type = BackupGoalTransactionType.Deposit,
            amountCents = 10_000,
            createdAtEpochMillis = 1_788_200_000_000,
        )
    }

    private fun sampleTemplate(
        accountRef: String = "account:main",
        categoryRef: String = "category:food",
    ): BackupTransactionTemplate {
        return BackupTransactionTemplate(
            ref = "template:lunch",
            name = "Lunch",
            type = BackupTransactionType.Expense,
            amountCents = 1_250,
            amountFixed = true,
            categoryRef = categoryRef,
            accountRef = accountRef,
            currencyCode = "USD",
            sortOrder = 1,
            createdAtEpochMillis = 1_788_200_000_000,
            updatedAtEpochMillis = 1_788_200_000_000,
        )
    }
}
