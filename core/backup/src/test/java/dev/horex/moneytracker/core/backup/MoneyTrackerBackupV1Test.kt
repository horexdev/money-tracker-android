package dev.horex.moneytracker.core.backup

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerBackupV1Test {
    @Test
    fun emptyBackupMatchesGoldenFixture() {
        val backup = MoneyTrackerBackup(createdAtEpochMillis = 0)

        val encoded = MoneyTrackerBackupV1Json.encodeToString(backup)

        assertEquals(
            fixture("money_tracker_backup_v1_empty.json").normalizedLineEndings().trim(),
            encoded.normalizedLineEndings().trim(),
        )
        assertEquals(backup, MoneyTrackerBackupV1Json.decodeFromString(encoded))
    }

    @Test
    fun sampleBackupUsesOnlyExportLocalRefs() {
        val backup = sampleBackup()

        MoneyTrackerBackupV1Contract.requireValidExportRefs(backup)
        val encoded = MoneyTrackerBackupV1Json.encodeToString(backup)
        val keys = collectJsonKeys(MoneyTrackerBackupV1Json.json.parseToJsonElement(encoded))

        val forbiddenKeys = keys.filter { key ->
            key == "id" ||
                key.endsWith("_id") ||
                forbiddenIdentifierParts.any { part -> part in key }
        }
        assertTrue("Forbidden identifier keys: $forbiddenKeys", forbiddenKeys.isEmpty())
    }

    @Test
    fun unresolvedRefsFailContractChecks() {
        val backup = sampleBackup(
            accountRef = "account:missing",
        )

        val error = assertThrows(MoneyTrackerBackupContractException::class.java) {
            MoneyTrackerBackupV1Contract.requireValidExportRefs(backup)
        }

        assertTrue(error.message.orEmpty().contains("Unresolved export ref"))
    }

    @Test
    fun duplicateRefsFailContractChecks() {
        val profile = sampleProfile().copy(
            accounts = listOf(
                sampleAccount("account:main"),
                sampleAccount("account:main"),
            ),
        )
        val backup = MoneyTrackerBackup(
            createdAtEpochMillis = 1_788_200_000_000,
            profiles = listOf(profile),
        )

        val error = assertThrows(MoneyTrackerBackupContractException::class.java) {
            MoneyTrackerBackupV1Contract.requireValidExportRefs(backup)
        }

        assertTrue(error.message.orEmpty().contains("Duplicate export ref"))
    }

    @Test
    fun forbiddenRefPartsFailContractChecks() {
        val backup = MoneyTrackerBackup(
            createdAtEpochMillis = 1_788_200_000_000,
            profiles = listOf(sampleProfile().copy(ref = "telegram:123")),
        )

        val error = assertThrows(MoneyTrackerBackupContractException::class.java) {
            MoneyTrackerBackupV1Contract.requireValidExportRefs(backup)
        }

        assertTrue(error.message.orEmpty().contains("Forbidden export ref part"))
    }

    @Test
    fun encodedSamplePreservesSerializableValues() {
        val encoded = MoneyTrackerBackupV1Json.encodeToString(sampleBackup())
        val root = MoneyTrackerBackupV1Json.json.parseToJsonElement(encoded).jsonObject
        val profile = root.getValue("profiles").jsonArray.first().jsonObject

        assertEquals(JsonPrimitive("MoneyTrackerBackup"), root.getValue("format"))
        assertEquals(JsonPrimitive(1), root.getValue("version"))
        assertEquals(JsonPrimitive("account:main"), profile.getValue("accounts").jsonArray.first().jsonObject.getValue("ref"))
        assertEquals(JsonPrimitive("category:food"), profile.getValue("transactions").jsonArray.first().jsonObject.getValue("category_ref"))
        assertFalse(encoded.contains("telegram", ignoreCase = true))
        assertFalse(encoded.contains("source", ignoreCase = true))
        assertFalse(encoded.contains("legacy", ignoreCase = true))
    }

    private fun fixture(name: String): String {
        return requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) {
            "Missing fixture $name"
        }.readText()
    }

    private fun collectJsonKeys(element: JsonElement): Set<String> {
        return when (element) {
            is JsonObject -> element.keys + element.values.flatMap { collectJsonKeys(it) }
            else -> element.jsonArrayOrNull()?.flatMap { collectJsonKeys(it) }?.toSet().orEmpty()
        }
    }

    private fun JsonElement.jsonArrayOrNull() = runCatching { jsonArray }.getOrNull()

    private fun String.normalizedLineEndings(): String = replace("\r\n", "\n")

    private fun sampleBackup(accountRef: String = "account:main"): MoneyTrackerBackup {
        return MoneyTrackerBackup(
            createdAtEpochMillis = 1_788_200_000_000,
            profiles = listOf(
                sampleProfile(transactionAccountRef = accountRef),
            ),
            exchangeRateSnapshots = listOf(
                BackupExchangeRateSnapshot(
                    snapshotDate = "2026-07-01",
                    baseCurrencyCode = "USD",
                    targetCurrencyCode = "EUR",
                    rateE8 = 92_000_000,
                    createdAtEpochMillis = 1_788_200_000_000,
                ),
            ),
        )
    }

    private fun sampleProfile(transactionAccountRef: String = "account:main"): BackupProfile {
        return BackupProfile(
            ref = "profile:main",
            label = "Offline profile",
            languageCode = "en",
            displayCurrencyCodes = listOf("EUR", "RUB"),
            createdAtEpochMillis = 1_788_200_000_000,
            updatedAtEpochMillis = 1_788_200_000_000,
            accounts = listOf(sampleAccount("account:main"), sampleAccount("account:savings")),
            categories = listOf(
                BackupCategory(
                    ref = "category:food",
                    name = "Food",
                    icon = "fork-knife",
                    type = BackupCategoryType.Expense,
                    color = "#ef4444",
                    isProtected = false,
                    updatedAtEpochMillis = 1_788_200_000_000,
                ),
                BackupCategory(
                    ref = "category:transfer",
                    name = "Transfer",
                    icon = "arrows-left-right",
                    type = BackupCategoryType.Transfer,
                    color = "#6366f1",
                    isProtected = true,
                    updatedAtEpochMillis = 1_788_200_000_000,
                ),
            ),
            transactions = listOf(
                BackupTransaction(
                    ref = "transaction:food-1",
                    type = BackupTransactionType.Expense,
                    amountCents = 1_250,
                    categoryRef = "category:food",
                    accountRef = transactionAccountRef,
                    note = "Lunch",
                    currencyCode = "USD",
                    snapshotDate = "2026-07-01",
                    createdAtEpochMillis = 1_788_200_000_000,
                ),
                BackupTransaction(
                    ref = "transaction:transfer-out-1",
                    type = BackupTransactionType.Expense,
                    amountCents = 2_000,
                    categoryRef = "category:transfer",
                    accountRef = "account:main",
                    currencyCode = "USD",
                    snapshotDate = "2026-07-01",
                    createdAtEpochMillis = 1_788_200_000_000,
                ),
            ),
            transfers = listOf(
                BackupTransfer(
                    ref = "transfer:1",
                    fromAccountRef = "account:main",
                    toAccountRef = "account:savings",
                    amountCents = 2_000,
                    fromCurrencyCode = "USD",
                    toCurrencyCode = "USD",
                    exchangeRateE8 = 100_000_000,
                    fromTransactionRef = "transaction:transfer-out-1",
                    toTransactionRef = null,
                    createdAtEpochMillis = 1_788_200_000_000,
                ),
            ),
            budgets = listOf(
                BackupBudget(
                    ref = "budget:food-monthly",
                    categoryRef = "category:food",
                    limitCents = 50_000,
                    period = BackupBudgetPeriod.Monthly,
                    currencyCode = "USD",
                    notifyAtPercent = 80,
                    notificationsEnabled = true,
                    lastNotifiedPercent = 0,
                    createdAtEpochMillis = 1_788_200_000_000,
                    updatedAtEpochMillis = 1_788_200_000_000,
                ),
            ),
            recurringTransactions = listOf(
                BackupRecurringTransaction(
                    ref = "recurring:rent",
                    accountRef = "account:main",
                    categoryRef = "category:food",
                    type = BackupTransactionType.Expense,
                    amountCents = 90_000,
                    currencyCode = "USD",
                    frequency = BackupRecurringFrequency.Monthly,
                    nextRunAtEpochMillis = 1_790_792_000_000,
                    isActive = true,
                    createdAtEpochMillis = 1_788_200_000_000,
                    updatedAtEpochMillis = 1_788_200_000_000,
                ),
            ),
            savingsGoals = listOf(
                BackupSavingsGoal(
                    ref = "goal:trip",
                    name = "Trip",
                    targetCents = 200_000,
                    currentCents = 10_000,
                    currencyCode = "USD",
                    accountRef = "account:savings",
                    createdAtEpochMillis = 1_788_200_000_000,
                    updatedAtEpochMillis = 1_788_200_000_000,
                ),
            ),
            goalTransactions = listOf(
                BackupGoalTransaction(
                    ref = "goal-transaction:trip-1",
                    goalRef = "goal:trip",
                    type = BackupGoalTransactionType.Deposit,
                    amountCents = 10_000,
                    createdAtEpochMillis = 1_788_200_000_000,
                ),
            ),
            exchangeRateOverrides = listOf(
                BackupExchangeRateOverride(
                    ref = "rate-override:usd-eur",
                    effectiveDate = "2026-07-01",
                    baseCurrencyCode = "USD",
                    targetCurrencyCode = "EUR",
                    rateE8 = 92_000_000,
                    createdAtEpochMillis = 1_788_200_000_000,
                    updatedAtEpochMillis = 1_788_200_000_000,
                ),
            ),
            transactionTemplates = listOf(
                BackupTransactionTemplate(
                    ref = "template:lunch",
                    name = "Lunch",
                    type = BackupTransactionType.Expense,
                    amountCents = 1_250,
                    amountFixed = true,
                    categoryRef = "category:food",
                    accountRef = "account:main",
                    currencyCode = "USD",
                    sortOrder = 1,
                    createdAtEpochMillis = 1_788_200_000_000,
                    updatedAtEpochMillis = 1_788_200_000_000,
                ),
            ),
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

    private companion object {
        val forbiddenIdentifierParts = listOf(
            "telegram",
            "init_data",
            "bot",
            "chat",
            "legacy",
            "source",
        )
    }
}
