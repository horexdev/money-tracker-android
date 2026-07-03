package dev.horex.moneytracker.core.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerBackupImporterTest {
    @Test
    fun importsOnlySelectedProfilesAndRemapsRefsToLocalIds() = runTest {
        val store = FakeImportStore()
        val importer = MoneyTrackerBackupImporter(store)
        val backup = MoneyTrackerBackup(
            createdAtEpochMillis = TEST_TIME,
            profiles = listOf(
                sampleProfile(
                    ref = "profile:first",
                    label = "First",
                    accountPrefix = "first",
                ),
                sampleProfile(
                    ref = "profile:second",
                    label = "Second",
                    accountPrefix = "second",
                ),
            ),
            exchangeRateSnapshots = listOf(sampleExchangeRateSnapshot()),
        )

        val result = importer.importBackup(
            backup = backup,
            options = MoneyTrackerBackupImportOptions(
                selectedProfileRefs = setOf("profile:second"),
            ),
        )

        assertEquals(1, result.importedProfileCount)
        assertEquals(1, result.importedExchangeRateSnapshots)
        assertEquals(
            MoneyTrackerBackupImportedProfileCounts(
                accounts = 2,
                categories = 2,
                transactions = 1,
                transfers = 1,
                budgets = 1,
                recurringTransactions = 1,
                savingsGoals = 1,
                goalTransactions = 1,
                exchangeRateOverrides = 1,
                transactionTemplates = 1,
            ),
            result.importedProfiles.single().counts,
        )

        val localProfileId = result.importedProfiles.single().localProfileId
        assertEquals(listOf(FakeProfile(id = localProfileId, label = "Second")), store.profiles)
        assertEquals(1, store.exchangeRateSnapshots.size)
        assertTrue(store.accounts.all { it.profileId == localProfileId })
        assertTrue(store.categories.all { it.profileId == localProfileId })

        val accountsByName = store.accounts.associateBy { it.name }
        val categoriesByName = store.categories.associateBy { it.name }
        val transaction = store.transactions.single()
        assertEquals(accountsByName.getValue("second Main").id, transaction.accountId)
        assertEquals(categoriesByName.getValue("Food second").id, transaction.categoryId)

        val transfer = store.transfers.single()
        assertEquals(accountsByName.getValue("second Main").id, transfer.fromAccountId)
        assertEquals(accountsByName.getValue("second Savings").id, transfer.toAccountId)
        assertEquals(transaction.id, transfer.fromTransactionId)

        assertEquals(categoriesByName.getValue("Food second").id, store.budgets.single().categoryId)
        assertEquals(accountsByName.getValue("second Main").id, store.recurringTransactions.single().accountId)
        assertEquals(categoriesByName.getValue("Food second").id, store.recurringTransactions.single().categoryId)
        assertEquals(accountsByName.getValue("second Savings").id, store.savingsGoals.single().accountId)
        assertEquals(store.savingsGoals.single().id, store.goalTransactions.single().goalId)
        assertEquals(accountsByName.getValue("second Main").id, store.transactionTemplates.single().accountId)
        assertEquals(categoriesByName.getValue("Food second").id, store.transactionTemplates.single().categoryId)
    }

    @Test
    fun importsLargeProfileInSingleRollbackScope() = runTest {
        val store = FakeImportStore()
        val importer = MoneyTrackerBackupImporter(store)
        val backup = MoneyTrackerBackup(
            createdAtEpochMillis = TEST_TIME,
            profiles = listOf(largeTransactionProfile(transactionCount = LARGE_IMPORT_TRANSACTION_COUNT)),
        )

        val result = importer.importBackup(backup)

        assertEquals(1, store.transactionsStarted)
        assertEquals(1, result.importedProfileCount)
        assertEquals(LARGE_IMPORT_TRANSACTION_COUNT, result.importedProfiles.single().counts.transactions)
        assertEquals(LARGE_IMPORT_TRANSACTION_COUNT, store.transactions.size)
        assertEquals(
            (1..LARGE_IMPORT_TRANSACTION_COUNT).toList(),
            store.transactions.map { it.amountCents.toInt() },
        )
    }

    @Test
    fun backupJsonKeepsMigrationIdentityFieldsOutOfContract() {
        val json = MoneyTrackerBackupV1Json.encodeToString(
            MoneyTrackerBackup(
                createdAtEpochMillis = TEST_TIME,
                profiles = listOf(sampleProfile(ref = "profile:main")),
            ),
        ).lowercase()

        listOf(
            "telegram",
            "init_data",
            "initdata",
            "bot_",
            "chat_",
            "legacy_",
            "source_",
            "user_id",
            "username",
            "first_name",
            "last_name",
        ).forEach { forbidden ->
            assertTrue("Backup JSON must not contain $forbidden", forbidden !in json)
        }
    }

    @Test
    fun validationErrorsPreventWritesBeforeTransaction() {
        val store = FakeImportStore()
        val importer = MoneyTrackerBackupImporter(store)
        val invalidBackup = MoneyTrackerBackup(
            createdAtEpochMillis = TEST_TIME,
            profiles = listOf(
                sampleProfile(ref = "profile:main").copy(
                    transactions = listOf(
                        sampleTransaction(
                            ref = "transaction:broken",
                            accountRef = "account:missing",
                            categoryRef = "category:food",
                        ),
                    ),
                ),
            ),
        )

        assertThrows(MoneyTrackerBackupImportValidationException::class.java) {
            runTest { importer.importBackup(invalidBackup) }
        }

        assertEquals(0, store.transactionsStarted)
        assertTrue(store.isEmpty())
    }

    @Test
    fun missingSelectedProfileFailsBeforeWrites() {
        val store = FakeImportStore()
        val importer = MoneyTrackerBackupImporter(store)
        val backup = MoneyTrackerBackup(
            createdAtEpochMillis = TEST_TIME,
            profiles = listOf(sampleProfile(ref = "profile:main")),
        )

        assertThrows(MoneyTrackerBackupImportSelectionException::class.java) {
            runTest {
                importer.importBackup(
                    backup = backup,
                    options = MoneyTrackerBackupImportOptions(
                        selectedProfileRefs = setOf("profile:missing"),
                    ),
                )
            }
        }

        assertEquals(0, store.transactionsStarted)
        assertTrue(store.isEmpty())
    }

    @Test
    fun rollsBackAllWritesWhenImportFailsInsideTransaction() {
        val store = FakeImportStore(failAt = FailurePoint.InsertTransaction)
        val importer = MoneyTrackerBackupImporter(store)
        val backup = MoneyTrackerBackup(
            createdAtEpochMillis = TEST_TIME,
            profiles = listOf(sampleProfile(ref = "profile:main")),
            exchangeRateSnapshots = listOf(sampleExchangeRateSnapshot()),
        )

        assertThrows(IllegalStateException::class.java) {
            runTest { importer.importBackup(backup) }
        }

        assertEquals(1, store.transactionsStarted)
        assertTrue(store.isEmpty())
    }

    private fun sampleProfile(
        ref: String,
        label: String = "Main",
        accountPrefix: String = "main",
    ): BackupProfile {
        val mainAccountRef = "account:$accountPrefix-main"
        val savingsAccountRef = "account:$accountPrefix-savings"
        val foodCategoryRef = "category:$accountPrefix-food"
        val transferCategoryRef = "category:$accountPrefix-transfer"
        val transactionRef = "transaction:$accountPrefix-lunch"
        val goalRef = "goal:$accountPrefix-trip"
        return BackupProfile(
            ref = ref,
            label = label,
            languageCode = "en",
            displayCurrencyCodes = listOf("usd", "EUR", "usd"),
            notificationPreferences = BackupNotificationPreferences(
                notifyBudgetAlerts = true,
                notifyRecurringReminders = true,
                notifyWeeklySummary = false,
                notifyGoalMilestones = true,
            ),
            uiPreferences = BackupUiPreferences(
                statsChartStyle = BackupStatsChartStyle.StackedBar,
                animateNumbers = true,
                theme = BackupTheme.Dark,
                hideAmounts = true,
            ),
            createdAtEpochMillis = TEST_TIME,
            updatedAtEpochMillis = TEST_TIME + 1,
            accounts = listOf(
                sampleAccount(mainAccountRef, "$accountPrefix Main", isDefault = true),
                sampleAccount(savingsAccountRef, "$accountPrefix Savings", isDefault = false),
            ),
            categories = listOf(
                sampleCategory(foodCategoryRef, "Food $accountPrefix", BackupCategoryType.Expense, isProtected = false),
                sampleCategory(transferCategoryRef, "Transfer $accountPrefix", BackupCategoryType.Transfer, isProtected = true),
            ),
            transactions = listOf(
                sampleTransaction(
                    ref = transactionRef,
                    accountRef = mainAccountRef,
                    categoryRef = foodCategoryRef,
                ),
            ),
            transfers = listOf(
                BackupTransfer(
                    ref = "transfer:$accountPrefix-main",
                    fromAccountRef = mainAccountRef,
                    toAccountRef = savingsAccountRef,
                    amountCents = 2_000,
                    fromCurrencyCode = "USD",
                    toCurrencyCode = "USD",
                    exchangeRateE8 = 100_000_000,
                    note = "Move",
                    fromTransactionRef = transactionRef,
                    createdAtEpochMillis = TEST_TIME,
                ),
            ),
            budgets = listOf(
                BackupBudget(
                    ref = "budget:$accountPrefix-food",
                    categoryRef = foodCategoryRef,
                    limitCents = 50_000,
                    period = BackupBudgetPeriod.Monthly,
                    currencyCode = "USD",
                    notifyAtPercent = 80,
                    notificationsEnabled = true,
                    lastNotifiedPercent = 0,
                    createdAtEpochMillis = TEST_TIME,
                    updatedAtEpochMillis = TEST_TIME,
                ),
            ),
            recurringTransactions = listOf(
                BackupRecurringTransaction(
                    ref = "recurring:$accountPrefix-rent",
                    accountRef = mainAccountRef,
                    categoryRef = foodCategoryRef,
                    type = BackupTransactionType.Expense,
                    amountCents = 90_000,
                    currencyCode = "USD",
                    frequency = BackupRecurringFrequency.Monthly,
                    nextRunAtEpochMillis = TEST_TIME + 86_400_000,
                    isActive = true,
                    createdAtEpochMillis = TEST_TIME,
                    updatedAtEpochMillis = TEST_TIME,
                ),
            ),
            savingsGoals = listOf(
                BackupSavingsGoal(
                    ref = goalRef,
                    name = "Trip $accountPrefix",
                    targetCents = 200_000,
                    currentCents = 10_000,
                    currencyCode = "USD",
                    accountRef = savingsAccountRef,
                    createdAtEpochMillis = TEST_TIME,
                    updatedAtEpochMillis = TEST_TIME,
                ),
            ),
            goalTransactions = listOf(
                BackupGoalTransaction(
                    ref = "goal-transaction:$accountPrefix-trip",
                    goalRef = goalRef,
                    type = BackupGoalTransactionType.Deposit,
                    amountCents = 10_000,
                    createdAtEpochMillis = TEST_TIME,
                ),
            ),
            exchangeRateOverrides = listOf(
                BackupExchangeRateOverride(
                    ref = "rate-override:$accountPrefix-usd-eur",
                    effectiveDate = "2026-07-01",
                    baseCurrencyCode = "USD",
                    targetCurrencyCode = "EUR",
                    rateE8 = 92_000_000,
                    createdAtEpochMillis = TEST_TIME,
                    updatedAtEpochMillis = TEST_TIME,
                ),
            ),
            transactionTemplates = listOf(
                BackupTransactionTemplate(
                    ref = "template:$accountPrefix-lunch",
                    name = "Lunch $accountPrefix",
                    type = BackupTransactionType.Expense,
                    amountCents = 1_250,
                    amountFixed = true,
                    categoryRef = foodCategoryRef,
                    accountRef = mainAccountRef,
                    currencyCode = "USD",
                    note = "Lunch",
                    sortOrder = 1,
                    createdAtEpochMillis = TEST_TIME,
                    updatedAtEpochMillis = TEST_TIME,
                ),
            ),
        )
    }

    private fun largeTransactionProfile(transactionCount: Int): BackupProfile {
        val accountRef = "account:large-main"
        val categoryRef = "category:large-food"
        return BackupProfile(
            ref = "profile:large",
            label = "Large",
            languageCode = "en",
            createdAtEpochMillis = TEST_TIME,
            updatedAtEpochMillis = TEST_TIME,
            accounts = listOf(sampleAccount(accountRef, "Large Main", isDefault = true)),
            categories = listOf(
                sampleCategory(categoryRef, "Large Food", BackupCategoryType.Expense, isProtected = false),
            ),
            transactions = (1..transactionCount).map { index ->
                BackupTransaction(
                    ref = "transaction:large-$index",
                    type = BackupTransactionType.Expense,
                    amountCents = index.toLong(),
                    categoryRef = categoryRef,
                    accountRef = accountRef,
                    note = "Large import $index",
                    currencyCode = "USD",
                    snapshotDate = "2026-07-01",
                    createdAtEpochMillis = TEST_TIME + index,
                )
            },
        )
    }

    private fun sampleAccount(ref: String, name: String, isDefault: Boolean): BackupAccount {
        return BackupAccount(
            ref = ref,
            name = name,
            icon = "wallet",
            color = "#6366f1",
            type = BackupAccountType.Checking,
            currencyCode = "USD",
            isDefault = isDefault,
            includeInTotal = true,
            createdAtEpochMillis = TEST_TIME,
            updatedAtEpochMillis = TEST_TIME,
        )
    }

    private fun sampleCategory(
        ref: String,
        name: String,
        type: BackupCategoryType,
        isProtected: Boolean,
    ): BackupCategory {
        return BackupCategory(
            ref = ref,
            name = name,
            icon = "tag",
            type = type,
            color = "#ef4444",
            isProtected = isProtected,
            updatedAtEpochMillis = TEST_TIME,
        )
    }

    private fun sampleTransaction(
        ref: String,
        accountRef: String,
        categoryRef: String,
    ): BackupTransaction {
        return BackupTransaction(
            ref = ref,
            type = BackupTransactionType.Expense,
            amountCents = 1_250,
            categoryRef = categoryRef,
            accountRef = accountRef,
            note = "Lunch",
            currencyCode = "USD",
            snapshotDate = "2026-07-01",
            createdAtEpochMillis = TEST_TIME,
        )
    }

    private fun sampleExchangeRateSnapshot(): BackupExchangeRateSnapshot {
        return BackupExchangeRateSnapshot(
            snapshotDate = "2026-07-01",
            baseCurrencyCode = "USD",
            targetCurrencyCode = "EUR",
            rateE8 = 92_000_000,
            createdAtEpochMillis = TEST_TIME,
        )
    }

    private companion object {
        const val TEST_TIME = 1_788_200_000_000L
        const val LARGE_IMPORT_TRANSACTION_COUNT = 500
    }
}

private enum class FailurePoint {
    InsertTransaction,
}

private class FakeImportStore(
    private val failAt: FailurePoint? = null,
) : MoneyTrackerBackupImportStore {
    var transactionsStarted = 0
        private set
    private var nextId = 1L

    val profiles = mutableListOf<FakeProfile>()
    val accounts = mutableListOf<FakeAccount>()
    val categories = mutableListOf<FakeCategory>()
    val transactions = mutableListOf<FakeTransaction>()
    val transfers = mutableListOf<FakeTransfer>()
    val budgets = mutableListOf<FakeBudget>()
    val recurringTransactions = mutableListOf<FakeRecurringTransaction>()
    val savingsGoals = mutableListOf<FakeSavingsGoal>()
    val goalTransactions = mutableListOf<FakeGoalTransaction>()
    val exchangeRateSnapshots = mutableListOf<FakeExchangeRateSnapshot>()
    val exchangeRateOverrides = mutableListOf<FakeExchangeRateOverride>()
    val transactionTemplates = mutableListOf<FakeTransactionTemplate>()

    override suspend fun <T> withTransaction(block: suspend MoneyTrackerBackupImportStore.() -> T): T {
        transactionsStarted += 1
        val snapshot = snapshot()
        return try {
            this.block()
        } catch (error: Throwable) {
            restore(snapshot)
            throw error
        }
    }

    override suspend fun insertProfile(profile: BackupProfile): Long {
        val id = nextId()
        profiles += FakeProfile(id = id, label = profile.label)
        return id
    }

    override suspend fun insertAccount(profileId: Long, account: BackupAccount): Long {
        val id = nextId()
        accounts += FakeAccount(id = id, profileId = profileId, name = account.name)
        return id
    }

    override suspend fun insertCategory(profileId: Long, category: BackupCategory): Long {
        val id = nextId()
        categories += FakeCategory(id = id, profileId = profileId, name = category.name)
        return id
    }

    override suspend fun insertTransaction(
        profileId: Long,
        transaction: BackupTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        failIf(FailurePoint.InsertTransaction)
        val id = nextId()
        transactions += FakeTransaction(
            id = id,
            profileId = profileId,
            accountId = accountId,
            categoryId = categoryId,
            amountCents = transaction.amountCents,
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
        val id = nextId()
        transfers += FakeTransfer(
            id = id,
            profileId = profileId,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            fromTransactionId = fromTransactionId,
            toTransactionId = toTransactionId,
        )
        return id
    }

    override suspend fun insertBudget(profileId: Long, budget: BackupBudget, categoryId: Long): Long {
        val id = nextId()
        budgets += FakeBudget(id = id, profileId = profileId, categoryId = categoryId)
        return id
    }

    override suspend fun insertRecurringTransaction(
        profileId: Long,
        recurring: BackupRecurringTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        val id = nextId()
        recurringTransactions += FakeRecurringTransaction(
            id = id,
            profileId = profileId,
            accountId = accountId,
            categoryId = categoryId,
        )
        return id
    }

    override suspend fun insertSavingsGoal(profileId: Long, goal: BackupSavingsGoal, accountId: Long?): Long {
        val id = nextId()
        savingsGoals += FakeSavingsGoal(id = id, profileId = profileId, accountId = accountId)
        return id
    }

    override suspend fun insertGoalTransaction(
        profileId: Long,
        transaction: BackupGoalTransaction,
        goalId: Long,
    ): Long {
        val id = nextId()
        goalTransactions += FakeGoalTransaction(id = id, profileId = profileId, goalId = goalId)
        return id
    }

    override suspend fun saveExchangeRateSnapshot(snapshot: BackupExchangeRateSnapshot): Long {
        val id = nextId()
        exchangeRateSnapshots += FakeExchangeRateSnapshot(id = id)
        return id
    }

    override suspend fun saveExchangeRateOverride(profileId: Long, override: BackupExchangeRateOverride): Long {
        val id = nextId()
        exchangeRateOverrides += FakeExchangeRateOverride(id = id, profileId = profileId)
        return id
    }

    override suspend fun insertTransactionTemplate(
        profileId: Long,
        template: BackupTransactionTemplate,
        accountId: Long,
        categoryId: Long,
    ): Long {
        val id = nextId()
        transactionTemplates += FakeTransactionTemplate(
            id = id,
            profileId = profileId,
            accountId = accountId,
            categoryId = categoryId,
        )
        return id
    }

    fun isEmpty(): Boolean {
        return profiles.isEmpty() &&
            accounts.isEmpty() &&
            categories.isEmpty() &&
            transactions.isEmpty() &&
            transfers.isEmpty() &&
            budgets.isEmpty() &&
            recurringTransactions.isEmpty() &&
            savingsGoals.isEmpty() &&
            goalTransactions.isEmpty() &&
            exchangeRateSnapshots.isEmpty() &&
            exchangeRateOverrides.isEmpty() &&
            transactionTemplates.isEmpty()
    }

    private fun nextId(): Long = nextId++

    private fun failIf(point: FailurePoint) {
        if (failAt == point) {
            throw IllegalStateException("Import failed at $point")
        }
    }

    private fun snapshot(): FakeImportSnapshot {
        return FakeImportSnapshot(
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

    private fun restore(snapshot: FakeImportSnapshot) {
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
}

private data class FakeImportSnapshot(
    val nextId: Long,
    val profiles: List<FakeProfile>,
    val accounts: List<FakeAccount>,
    val categories: List<FakeCategory>,
    val transactions: List<FakeTransaction>,
    val transfers: List<FakeTransfer>,
    val budgets: List<FakeBudget>,
    val recurringTransactions: List<FakeRecurringTransaction>,
    val savingsGoals: List<FakeSavingsGoal>,
    val goalTransactions: List<FakeGoalTransaction>,
    val exchangeRateSnapshots: List<FakeExchangeRateSnapshot>,
    val exchangeRateOverrides: List<FakeExchangeRateOverride>,
    val transactionTemplates: List<FakeTransactionTemplate>,
)

private data class FakeProfile(val id: Long, val label: String)
private data class FakeAccount(val id: Long, val profileId: Long, val name: String)
private data class FakeCategory(val id: Long, val profileId: Long, val name: String)
private data class FakeTransaction(
    val id: Long,
    val profileId: Long,
    val accountId: Long,
    val categoryId: Long,
    val amountCents: Long,
)
private data class FakeTransfer(
    val id: Long,
    val profileId: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val fromTransactionId: Long?,
    val toTransactionId: Long?,
)
private data class FakeBudget(val id: Long, val profileId: Long, val categoryId: Long)
private data class FakeRecurringTransaction(val id: Long, val profileId: Long, val accountId: Long, val categoryId: Long)
private data class FakeSavingsGoal(val id: Long, val profileId: Long, val accountId: Long?)
private data class FakeGoalTransaction(val id: Long, val profileId: Long, val goalId: Long)
private data class FakeExchangeRateSnapshot(val id: Long)
private data class FakeExchangeRateOverride(val id: Long, val profileId: Long)
private data class FakeTransactionTemplate(val id: Long, val profileId: Long, val accountId: Long, val categoryId: Long)
