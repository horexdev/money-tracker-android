package dev.horex.moneytracker.core.backup

import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.BudgetEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateOverrideEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateSnapshotEntity
import dev.horex.moneytracker.core.database.model.GoalTransactionEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionEntity
import dev.horex.moneytracker.core.database.model.SavingsGoalEntity
import dev.horex.moneytracker.core.database.model.SystemCategoryLocalization
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.model.TransactionTemplateEntity
import dev.horex.moneytracker.core.database.model.TransferEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

private const val TEST_TIME = 1_788_200_000_000L
private const val EXPORT_TIME = TEST_TIME + 10_000

private val forbiddenIdentifierParts = listOf(
    "telegram",
    "init_data",
    "bot",
    "chat",
    "legacy",
    "source",
)
private val forbiddenJsonFragments = forbiddenIdentifierParts + listOf(
    "sqlcipher",
    "passphrase",
    "keystore",
    "device-bound",
    "secret",
    "account:501",
    "transaction:701",
    "profile:901",
)

class MoneyTrackerBackupExporterTest {
    @Test
    fun exportedBackupRoundTripsWithoutLeakingLocalIdsOrDeviceSecrets() = runTest {
        val source = InMemoryBackupDatabase.seeded()
        val exporter = MoneyTrackerBackupExporter(
            store = InMemoryBackupExportStore(source),
            currentTimeMillis = { EXPORT_TIME },
        )

        val exported = exporter.exportBackup()
        val encoded = MoneyTrackerBackupV1Json.encodeToString(exported)

        assertEquals(EXPORT_TIME, exported.createdAtEpochMillis)
        assertEquals("profile:1", exported.profiles.single().ref)
        assertEquals(listOf("account:1", "account:2"), exported.profiles.single().accounts.map { it.ref })
        assertEquals(
            mapOf(
                "Food" to SystemCategoryLocalization.FOOD,
                "Transfer" to SystemCategoryLocalization.TRANSFER,
                "Archived" to null,
            ),
            exported.profiles.single().categories.associate { it.name to it.localizationKey },
        )
        assertEquals(listOf("transaction:1", "transaction:2", "transaction:3"), exported.profiles.single().transactions.map { it.ref })
        assertPortableJson(encoded)

        val target = InMemoryBackupDatabase()
        val importResult = MoneyTrackerBackupImporter(InMemoryBackupImportStore(target))
            .importBackup(MoneyTrackerBackupV1Json.decodeFromString(encoded))

        assertEquals(1, importResult.importedProfileCount)
        assertEquals(1, importResult.importedExchangeRateSnapshots)
        assertEquals(
            MoneyTrackerBackupImportedProfileCounts(
                accounts = 2,
                categories = 3,
                transactions = 3,
                transfers = 1,
                budgets = 1,
                recurringTransactions = 1,
                savingsGoals = 1,
                goalTransactions = 1,
                exchangeRateOverrides = 1,
                transactionTemplates = 1,
            ),
            importResult.importedProfiles.single().counts,
        )

        val reexported = MoneyTrackerBackupExporter(
            store = InMemoryBackupExportStore(target),
            currentTimeMillis = { EXPORT_TIME },
        ).exportBackup()

        assertEquals(exported, reexported)
    }

    @Test
    fun missingSelectedLocalProfileFailsExportBeforeDocumentWrite() {
        val exporter = MoneyTrackerBackupExporter(
            store = InMemoryBackupExportStore(InMemoryBackupDatabase.seeded()),
            currentTimeMillis = { EXPORT_TIME },
        )

        assertThrows(MoneyTrackerBackupExportSelectionException::class.java) {
            runTest {
                exporter.exportBackup(
                    MoneyTrackerBackupExportOptions(
                        selectedLocalProfileIds = setOf(404),
                    ),
                )
            }
        }
    }

    private fun assertPortableJson(encoded: String) {
        val keys = collectJsonKeys(MoneyTrackerBackupV1Json.json.parseToJsonElement(encoded))
        val forbiddenKeys = keys.filter { key ->
            key == "id" ||
                key.endsWith("_id") ||
                forbiddenIdentifierParts.any { part -> part in key }
        }
        assertTrue("Forbidden identifier keys: $forbiddenKeys", forbiddenKeys.isEmpty())

        forbiddenJsonFragments.forEach { fragment ->
            assertFalse(
                "Backup JSON must not contain '$fragment'.",
                encoded.contains(fragment, ignoreCase = true),
            )
        }
    }

    private fun collectJsonKeys(element: JsonElement): Set<String> {
        return when (element) {
            is JsonObject -> element.keys + element.values.flatMap { collectJsonKeys(it) }
            is JsonArray -> element.flatMap { collectJsonKeys(it) }.toSet()
            else -> emptySet()
        }
    }

}

private class InMemoryBackupDatabase(
    private var nextId: Long = 1,
) {
    val profiles = mutableListOf<LocalProfileEntity>()
    val accounts = mutableListOf<AccountEntity>()
    val categories = mutableListOf<CategoryEntity>()
    val transactions = mutableListOf<TransactionEntity>()
    val transfers = mutableListOf<TransferEntity>()
    val budgets = mutableListOf<BudgetEntity>()
    val recurringTransactions = mutableListOf<RecurringTransactionEntity>()
    val savingsGoals = mutableListOf<SavingsGoalEntity>()
    val goalTransactions = mutableListOf<GoalTransactionEntity>()
    val exchangeRateSnapshots = mutableListOf<ExchangeRateSnapshotEntity>()
    val exchangeRateOverrides = mutableListOf<ExchangeRateOverrideEntity>()
    val transactionTemplates = mutableListOf<TransactionTemplateEntity>()

    fun nextId(): Long = nextId++

    fun snapshot(): InMemoryBackupSnapshot {
        return InMemoryBackupSnapshot(
            nextId = nextId,
            profiles = profiles.toList(),
            accounts = accounts.toList(),
            categories = categories.toList(),
            transactions = transactions.toList(),
            transfers = transfers.toList(),
            budgets = budgets.toList(),
            recurringTransactions = recurringTransactions.toList(),
            savingsGoals = savingsGoals.toList(),
            goalTransactions = goalTransactions.toList(),
            exchangeRateSnapshots = exchangeRateSnapshots.toList(),
            exchangeRateOverrides = exchangeRateOverrides.toList(),
            transactionTemplates = transactionTemplates.toList(),
        )
    }

    fun restore(snapshot: InMemoryBackupSnapshot) {
        nextId = snapshot.nextId
        profiles.replaceWith(snapshot.profiles)
        accounts.replaceWith(snapshot.accounts)
        categories.replaceWith(snapshot.categories)
        transactions.replaceWith(snapshot.transactions)
        transfers.replaceWith(snapshot.transfers)
        budgets.replaceWith(snapshot.budgets)
        recurringTransactions.replaceWith(snapshot.recurringTransactions)
        savingsGoals.replaceWith(snapshot.savingsGoals)
        goalTransactions.replaceWith(snapshot.goalTransactions)
        exchangeRateSnapshots.replaceWith(snapshot.exchangeRateSnapshots)
        exchangeRateOverrides.replaceWith(snapshot.exchangeRateOverrides)
        transactionTemplates.replaceWith(snapshot.transactionTemplates)
    }

    private fun <T> MutableList<T>.replaceWith(items: List<T>) {
        clear()
        addAll(items)
    }

    companion object {
        fun seeded(): InMemoryBackupDatabase {
            val database = InMemoryBackupDatabase(nextId = 10_000)
            val profileId = 901L
            val cashAccountId = 501L
            val savingsAccountId = 777L
            val foodCategoryId = 601L
            val transferCategoryId = 602L
            val archivedCategoryId = 603L
            val lunchTransactionId = 701L
            val transferOutTransactionId = 702L
            val transferInTransactionId = 703L
            val savingsGoalId = 831L

            database.profiles += LocalProfileEntity(
                id = profileId,
                label = "Offline profile",
                languageCode = "en",
                displayCurrenciesCsv = "USD,EUR",
                notifyBudgetAlerts = true,
                notifyRecurringReminders = true,
                notifyWeeklySummary = false,
                notifyGoalMilestones = true,
                statsChartStyle = "stacked_bar",
                animateNumbers = true,
                theme = "dark",
                hideAmounts = true,
                createdAtEpochMillis = TEST_TIME,
                updatedAtEpochMillis = TEST_TIME + 1,
            )
            database.accounts += AccountEntity(
                id = cashAccountId,
                profileId = profileId,
                name = "Cash",
                icon = "wallet",
                color = "#6366f1",
                type = "cash",
                currencyCode = "USD",
                isDefault = true,
                includeInTotal = true,
                createdAtEpochMillis = TEST_TIME,
                updatedAtEpochMillis = TEST_TIME,
            )
            database.accounts += AccountEntity(
                id = savingsAccountId,
                profileId = profileId,
                name = "Savings",
                icon = "piggy-bank",
                color = "#14b8a6",
                type = "savings",
                currencyCode = "EUR",
                isDefault = false,
                includeInTotal = false,
                createdAtEpochMillis = TEST_TIME + 1,
                updatedAtEpochMillis = TEST_TIME + 1,
            )
            database.categories += CategoryEntity(
                id = foodCategoryId,
                profileId = profileId,
                name = "Food",
                localizationKey = SystemCategoryLocalization.FOOD,
                icon = "fork-knife",
                type = "expense",
                color = "#f97316",
                isProtected = false,
                updatedAtEpochMillis = TEST_TIME,
            )
            database.categories += CategoryEntity(
                id = transferCategoryId,
                profileId = profileId,
                name = "Transfer",
                localizationKey = SystemCategoryLocalization.TRANSFER,
                icon = "arrows-left-right",
                type = "transfer",
                color = "#6366f1",
                isProtected = true,
                updatedAtEpochMillis = TEST_TIME + 1,
            )
            database.categories += CategoryEntity(
                id = archivedCategoryId,
                profileId = profileId,
                name = "Archived",
                icon = "archive",
                type = "income",
                color = "#71717a",
                isProtected = false,
                updatedAtEpochMillis = TEST_TIME + 2,
                deletedAtEpochMillis = TEST_TIME + 3,
            )
            database.transactions += TransactionEntity(
                id = lunchTransactionId,
                profileId = profileId,
                type = "expense",
                amountCents = 1_250,
                categoryId = foodCategoryId,
                accountId = cashAccountId,
                note = "Lunch",
                currencyCode = "USD",
                snapshotDate = "2026-07-01",
                createdAtEpochMillis = TEST_TIME,
            )
            database.transactions += TransactionEntity(
                id = transferOutTransactionId,
                profileId = profileId,
                type = "expense",
                amountCents = 2_000,
                categoryId = transferCategoryId,
                accountId = cashAccountId,
                note = "Move",
                currencyCode = "USD",
                snapshotDate = "2026-07-01",
                createdAtEpochMillis = TEST_TIME + 1,
            )
            database.transactions += TransactionEntity(
                id = transferInTransactionId,
                profileId = profileId,
                type = "income",
                amountCents = 1_840,
                categoryId = transferCategoryId,
                accountId = savingsAccountId,
                note = "Move",
                currencyCode = "EUR",
                snapshotDate = "2026-07-01",
                createdAtEpochMillis = TEST_TIME + 2,
            )
            database.transfers += TransferEntity(
                id = 801,
                profileId = profileId,
                fromAccountId = cashAccountId,
                toAccountId = savingsAccountId,
                amountCents = 2_000,
                fromCurrencyCode = "USD",
                toCurrencyCode = "EUR",
                exchangeRateE8 = 92_000_000,
                note = "Move",
                fromTransactionId = transferOutTransactionId,
                toTransactionId = transferInTransactionId,
                createdAtEpochMillis = TEST_TIME + 3,
            )
            database.budgets += BudgetEntity(
                id = 811,
                profileId = profileId,
                categoryId = foodCategoryId,
                limitCents = 50_000,
                period = "monthly",
                currencyCode = "USD",
                notifyAtPercent = 80,
                notificationsEnabled = true,
                lastNotifiedPercent = 0,
                createdAtEpochMillis = TEST_TIME,
                updatedAtEpochMillis = TEST_TIME,
            )
            database.recurringTransactions += RecurringTransactionEntity(
                id = 821,
                profileId = profileId,
                accountId = cashAccountId,
                categoryId = foodCategoryId,
                type = "expense",
                amountCents = 90_000,
                currencyCode = "USD",
                note = "Rent",
                frequency = "monthly",
                nextRunAtEpochMillis = TEST_TIME + 86_400_000,
                isActive = true,
                createdAtEpochMillis = TEST_TIME,
                updatedAtEpochMillis = TEST_TIME,
            )
            database.savingsGoals += SavingsGoalEntity(
                id = savingsGoalId,
                profileId = profileId,
                name = "Trip",
                targetCents = 200_000,
                currentCents = 10_000,
                currencyCode = "EUR",
                deadlineDate = "2026-12-31",
                accountId = savingsAccountId,
                createdAtEpochMillis = TEST_TIME,
                updatedAtEpochMillis = TEST_TIME,
            )
            database.goalTransactions += GoalTransactionEntity(
                id = 841,
                profileId = profileId,
                goalId = savingsGoalId,
                type = "deposit",
                amountCents = 10_000,
                createdAtEpochMillis = TEST_TIME,
            )
            database.exchangeRateSnapshots += ExchangeRateSnapshotEntity(
                id = 851,
                snapshotDate = "2026-07-01",
                baseCurrency = "USD",
                targetCurrency = "EUR",
                rateE8 = 92_000_000,
                createdAtEpochMillis = TEST_TIME,
            )
            database.exchangeRateOverrides += ExchangeRateOverrideEntity(
                id = 861,
                profileId = profileId,
                effectiveDate = "2026-07-01",
                baseCurrency = "USD",
                targetCurrency = "EUR",
                rateE8 = 92_000_000,
                createdAtEpochMillis = TEST_TIME,
                updatedAtEpochMillis = TEST_TIME,
            )
            database.transactionTemplates += TransactionTemplateEntity(
                id = 871,
                profileId = profileId,
                name = "Lunch",
                type = "expense",
                amountCents = 1_250,
                amountFixed = true,
                categoryId = foodCategoryId,
                accountId = cashAccountId,
                currencyCode = "USD",
                note = "Lunch",
                sortOrder = 1,
                createdAtEpochMillis = TEST_TIME,
                updatedAtEpochMillis = TEST_TIME,
            )
            return database
        }
    }
}

private data class InMemoryBackupSnapshot(
    val nextId: Long,
    val profiles: List<LocalProfileEntity>,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val transfers: List<TransferEntity>,
    val budgets: List<BudgetEntity>,
    val recurringTransactions: List<RecurringTransactionEntity>,
    val savingsGoals: List<SavingsGoalEntity>,
    val goalTransactions: List<GoalTransactionEntity>,
    val exchangeRateSnapshots: List<ExchangeRateSnapshotEntity>,
    val exchangeRateOverrides: List<ExchangeRateOverrideEntity>,
    val transactionTemplates: List<TransactionTemplateEntity>,
)

private class InMemoryBackupExportStore(
    private val database: InMemoryBackupDatabase,
) : MoneyTrackerBackupExportStore {
    override suspend fun <T> withTransaction(block: suspend MoneyTrackerBackupExportStore.() -> T): T {
        return this.block()
    }

    override suspend fun listProfiles(): List<LocalProfileEntity> {
        return database.profiles.sortedWith(compareBy<LocalProfileEntity> { it.createdAtEpochMillis }.thenBy { it.id })
    }

    override suspend fun listAccounts(profileId: Long): List<AccountEntity> {
        return database.accounts
            .filter { it.profileId == profileId }
            .sortedWith(compareBy<AccountEntity> { it.createdAtEpochMillis }.thenBy { it.id })
    }

    override suspend fun listCategories(profileId: Long): List<CategoryEntity> {
        return database.categories
            .filter { it.profileId == profileId }
            .sortedWith(compareBy<CategoryEntity> { it.updatedAtEpochMillis }.thenBy { it.id })
    }

    override suspend fun listTransactions(profileId: Long): List<TransactionEntity> {
        return database.transactions
            .filter { it.profileId == profileId }
            .sortedWith(compareBy<TransactionEntity> { it.createdAtEpochMillis }.thenBy { it.id })
    }

    override suspend fun listTransfers(profileId: Long): List<TransferEntity> {
        return database.transfers
            .filter { it.profileId == profileId }
            .sortedWith(compareBy<TransferEntity> { it.createdAtEpochMillis }.thenBy { it.id })
    }

    override suspend fun listBudgets(profileId: Long): List<BudgetEntity> {
        return database.budgets
            .filter { it.profileId == profileId }
            .sortedWith(compareBy<BudgetEntity> { it.createdAtEpochMillis }.thenBy { it.id })
    }

    override suspend fun listRecurringTransactions(profileId: Long): List<RecurringTransactionEntity> {
        return database.recurringTransactions
            .filter { it.profileId == profileId }
            .sortedWith(compareBy<RecurringTransactionEntity> { it.createdAtEpochMillis }.thenBy { it.id })
    }

    override suspend fun listSavingsGoals(profileId: Long): List<SavingsGoalEntity> {
        return database.savingsGoals
            .filter { it.profileId == profileId }
            .sortedWith(compareBy<SavingsGoalEntity> { it.createdAtEpochMillis }.thenBy { it.id })
    }

    override suspend fun listGoalTransactions(profileId: Long): List<GoalTransactionEntity> {
        return database.goalTransactions
            .filter { it.profileId == profileId }
            .sortedWith(compareBy<GoalTransactionEntity> { it.createdAtEpochMillis }.thenBy { it.id })
    }

    override suspend fun listExchangeRateSnapshots(): List<ExchangeRateSnapshotEntity> {
        return database.exchangeRateSnapshots.sortedWith(
            compareBy<ExchangeRateSnapshotEntity> { it.snapshotDate }
                .thenBy { it.baseCurrency }
                .thenBy { it.targetCurrency }
                .thenBy { it.id },
        )
    }

    override suspend fun listExchangeRateOverrides(profileId: Long): List<ExchangeRateOverrideEntity> {
        return database.exchangeRateOverrides
            .filter { it.profileId == profileId }
            .sortedWith(
                compareBy<ExchangeRateOverrideEntity> { it.effectiveDate }
                    .thenBy { it.baseCurrency }
                    .thenBy { it.targetCurrency }
                    .thenBy { it.id },
            )
    }

    override suspend fun listTransactionTemplates(profileId: Long): List<TransactionTemplateEntity> {
        return database.transactionTemplates
            .filter { it.profileId == profileId }
            .sortedWith(
                compareBy<TransactionTemplateEntity> { it.sortOrder }
                    .thenBy { it.createdAtEpochMillis }
                    .thenBy { it.id },
            )
    }
}

private class InMemoryBackupImportStore(
    private val database: InMemoryBackupDatabase,
) : MoneyTrackerBackupImportStore {
    override suspend fun <T> withTransaction(block: suspend MoneyTrackerBackupImportStore.() -> T): T {
        val snapshot = database.snapshot()
        return try {
            this.block()
        } catch (error: Throwable) {
            database.restore(snapshot)
            throw error
        }
    }

    override suspend fun insertProfile(profile: BackupProfile): Long {
        val id = database.nextId()
        database.profiles += LocalProfileEntity(
            id = id,
            label = profile.label.trim(),
            languageCode = profile.languageCode,
            displayCurrenciesCsv = profile.displayCurrencyCodes.toStorageCsv(),
            notifyBudgetAlerts = profile.notificationPreferences.notifyBudgetAlerts,
            notifyRecurringReminders = profile.notificationPreferences.notifyRecurringReminders,
            notifyWeeklySummary = profile.notificationPreferences.notifyWeeklySummary,
            notifyGoalMilestones = profile.notificationPreferences.notifyGoalMilestones,
            statsChartStyle = profile.uiPreferences.statsChartStyle.storageValue,
            animateNumbers = profile.uiPreferences.animateNumbers,
            theme = profile.uiPreferences.theme.storageValue,
            hideAmounts = profile.uiPreferences.hideAmounts,
            createdAtEpochMillis = profile.createdAtEpochMillis,
            updatedAtEpochMillis = profile.updatedAtEpochMillis,
        )
        return id
    }

    override suspend fun insertAccount(profileId: Long, account: BackupAccount): Long {
        val id = database.nextId()
        database.accounts += AccountEntity(
            id = id,
            profileId = profileId,
            name = account.name,
            icon = account.icon,
            color = account.color,
            type = account.type.storageValue,
            currencyCode = account.currencyCode,
            isDefault = account.isDefault,
            includeInTotal = account.includeInTotal,
            createdAtEpochMillis = account.createdAtEpochMillis,
            updatedAtEpochMillis = account.updatedAtEpochMillis,
        )
        return id
    }

    override suspend fun insertCategory(profileId: Long, category: BackupCategory): Long {
        val id = database.nextId()
        database.categories += CategoryEntity(
            id = id,
            profileId = profileId,
            name = category.name,
            localizationKey = category.localizationKey,
            icon = category.icon,
            type = category.type.storageValue,
            color = category.color,
            isProtected = category.isProtected,
            updatedAtEpochMillis = category.updatedAtEpochMillis,
            deletedAtEpochMillis = category.deletedAtEpochMillis,
        )
        return id
    }

    override suspend fun insertTransaction(
        profileId: Long,
        transaction: BackupTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        val id = database.nextId()
        database.transactions += TransactionEntity(
            id = id,
            profileId = profileId,
            type = transaction.type.storageValue,
            amountCents = transaction.amountCents,
            categoryId = categoryId,
            accountId = accountId,
            note = transaction.note,
            currencyCode = transaction.currencyCode,
            snapshotDate = transaction.snapshotDate,
            isAdjustment = transaction.isAdjustment,
            createdAtEpochMillis = transaction.createdAtEpochMillis,
        )
        return id
    }

    override suspend fun insertTransfer(
        profileId: Long,
        transfer: BackupTransfer,
        fromAccountId: Long,
        toAccountId: Long,
        fromTransactionId: Long?,
        toTransactionId: Long?,
    ): Long {
        val id = database.nextId()
        database.transfers += TransferEntity(
            id = id,
            profileId = profileId,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amountCents = transfer.amountCents,
            fromCurrencyCode = transfer.fromCurrencyCode,
            toCurrencyCode = transfer.toCurrencyCode,
            exchangeRateE8 = transfer.exchangeRateE8,
            note = transfer.note,
            fromTransactionId = fromTransactionId,
            toTransactionId = toTransactionId,
            createdAtEpochMillis = transfer.createdAtEpochMillis,
        )
        return id
    }

    override suspend fun insertBudget(profileId: Long, budget: BackupBudget, categoryId: Long): Long {
        val id = database.nextId()
        database.budgets += BudgetEntity(
            id = id,
            profileId = profileId,
            categoryId = categoryId,
            limitCents = budget.limitCents,
            period = budget.period.storageValue,
            currencyCode = budget.currencyCode,
            notifyAtPercent = budget.notifyAtPercent,
            notificationsEnabled = budget.notificationsEnabled,
            lastNotifiedPercent = budget.lastNotifiedPercent,
            lastNotifiedAtEpochMillis = budget.lastNotifiedAtEpochMillis,
            createdAtEpochMillis = budget.createdAtEpochMillis,
            updatedAtEpochMillis = budget.updatedAtEpochMillis,
        )
        return id
    }

    override suspend fun insertRecurringTransaction(
        profileId: Long,
        recurring: BackupRecurringTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        val id = database.nextId()
        database.recurringTransactions += RecurringTransactionEntity(
            id = id,
            profileId = profileId,
            accountId = accountId,
            categoryId = categoryId,
            type = recurring.type.storageValue,
            amountCents = recurring.amountCents,
            currencyCode = recurring.currencyCode,
            note = recurring.note,
            frequency = recurring.frequency.storageValue,
            nextRunAtEpochMillis = recurring.nextRunAtEpochMillis,
            isActive = recurring.isActive,
            createdAtEpochMillis = recurring.createdAtEpochMillis,
            updatedAtEpochMillis = recurring.updatedAtEpochMillis,
        )
        return id
    }

    override suspend fun insertSavingsGoal(profileId: Long, goal: BackupSavingsGoal, accountId: Long?): Long {
        val id = database.nextId()
        database.savingsGoals += SavingsGoalEntity(
            id = id,
            profileId = profileId,
            name = goal.name,
            targetCents = goal.targetCents,
            currentCents = goal.currentCents,
            currencyCode = goal.currencyCode,
            deadlineDate = goal.deadlineDate,
            accountId = accountId,
            createdAtEpochMillis = goal.createdAtEpochMillis,
            updatedAtEpochMillis = goal.updatedAtEpochMillis,
        )
        return id
    }

    override suspend fun insertGoalTransaction(
        profileId: Long,
        transaction: BackupGoalTransaction,
        goalId: Long,
    ): Long {
        val id = database.nextId()
        database.goalTransactions += GoalTransactionEntity(
            id = id,
            profileId = profileId,
            goalId = goalId,
            type = transaction.type.storageValue,
            amountCents = transaction.amountCents,
            createdAtEpochMillis = transaction.createdAtEpochMillis,
        )
        return id
    }

    override suspend fun saveExchangeRateSnapshot(snapshot: BackupExchangeRateSnapshot): Long {
        val id = database.nextId()
        database.exchangeRateSnapshots += ExchangeRateSnapshotEntity(
            id = id,
            snapshotDate = snapshot.snapshotDate,
            baseCurrency = snapshot.baseCurrencyCode,
            targetCurrency = snapshot.targetCurrencyCode,
            rateE8 = snapshot.rateE8,
            createdAtEpochMillis = snapshot.createdAtEpochMillis,
        )
        return id
    }

    override suspend fun saveExchangeRateOverride(profileId: Long, override: BackupExchangeRateOverride): Long {
        val id = database.nextId()
        database.exchangeRateOverrides += ExchangeRateOverrideEntity(
            id = id,
            profileId = profileId,
            effectiveDate = override.effectiveDate,
            baseCurrency = override.baseCurrencyCode,
            targetCurrency = override.targetCurrencyCode,
            rateE8 = override.rateE8,
            createdAtEpochMillis = override.createdAtEpochMillis,
            updatedAtEpochMillis = override.updatedAtEpochMillis,
        )
        return id
    }

    override suspend fun insertTransactionTemplate(
        profileId: Long,
        template: BackupTransactionTemplate,
        accountId: Long,
        categoryId: Long,
    ): Long {
        val id = database.nextId()
        database.transactionTemplates += TransactionTemplateEntity(
            id = id,
            profileId = profileId,
            name = template.name,
            type = template.type.storageValue,
            amountCents = template.amountCents,
            amountFixed = template.amountFixed,
            categoryId = categoryId,
            accountId = accountId,
            currencyCode = template.currencyCode,
            note = template.note,
            sortOrder = template.sortOrder,
            createdAtEpochMillis = template.createdAtEpochMillis,
            updatedAtEpochMillis = template.updatedAtEpochMillis,
        )
        return id
    }
}

private fun List<String>.toStorageCsv(): String {
    return map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(separator = ",")
}

private val BackupAccountType.storageValue: String
    get() = when (this) {
        BackupAccountType.Checking -> "checking"
        BackupAccountType.Savings -> "savings"
        BackupAccountType.Cash -> "cash"
        BackupAccountType.Credit -> "credit"
        BackupAccountType.Crypto -> "crypto"
    }

private val BackupCategoryType.storageValue: String
    get() = when (this) {
        BackupCategoryType.Expense -> "expense"
        BackupCategoryType.Income -> "income"
        BackupCategoryType.Both -> "both"
        BackupCategoryType.Savings -> "savings"
        BackupCategoryType.Transfer -> "transfer"
        BackupCategoryType.Adjustment -> "adjustment"
    }

private val BackupTransactionType.storageValue: String
    get() = when (this) {
        BackupTransactionType.Expense -> "expense"
        BackupTransactionType.Income -> "income"
    }

private val BackupBudgetPeriod.storageValue: String
    get() = when (this) {
        BackupBudgetPeriod.Weekly -> "weekly"
        BackupBudgetPeriod.Monthly -> "monthly"
    }

private val BackupRecurringFrequency.storageValue: String
    get() = when (this) {
        BackupRecurringFrequency.Daily -> "daily"
        BackupRecurringFrequency.Weekly -> "weekly"
        BackupRecurringFrequency.Monthly -> "monthly"
        BackupRecurringFrequency.Yearly -> "yearly"
    }

private val BackupGoalTransactionType.storageValue: String
    get() = when (this) {
        BackupGoalTransactionType.Deposit -> "deposit"
        BackupGoalTransactionType.Withdraw -> "withdraw"
    }

private val BackupStatsChartStyle.storageValue: String
    get() = when (this) {
        BackupStatsChartStyle.Donut -> "donut"
        BackupStatsChartStyle.StackedBar -> "stacked_bar"
        BackupStatsChartStyle.DualBar -> "dual_bar"
        BackupStatsChartStyle.ProfitBars -> "profit_bars"
    }

private val BackupTheme.storageValue: String
    get() = when (this) {
        BackupTheme.System -> "system"
        BackupTheme.Light -> "light"
        BackupTheme.Dark -> "dark"
    }
