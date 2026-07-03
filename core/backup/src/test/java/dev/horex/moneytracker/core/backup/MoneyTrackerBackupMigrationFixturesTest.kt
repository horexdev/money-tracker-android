package dev.horex.moneytracker.core.backup

import dev.horex.moneytracker.core.database.model.SystemCategoryLocalization
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerBackupMigrationFixturesTest {
    @Test
    fun anonymizedFullDomainFixtureMatchesGoldenJsonAndRoundTrips() {
        val encodedFixture = fixture(FULL_DOMAIN_FIXTURE).normalizedLineEndings().trim()
        val backup = MoneyTrackerBackupV1Json.decodeFromString(encodedFixture)

        assertFullDomainCoverage(backup)

        val encoded = MoneyTrackerBackupV1Json.encodeToString(backup)

        assertEquals(encodedFixture, encoded.normalizedLineEndings().trim())
        assertEquals(backup, MoneyTrackerBackupV1Json.decodeFromString(encoded))
    }

    @Test
    fun anonymizedFullDomainFixtureIsCleanForRestoreDryRun() {
        val encodedFixture = fixture(FULL_DOMAIN_FIXTURE)

        val result = MoneyTrackerBackupV1Validator.validateJson(encodedFixture)

        assertTrue(result.issues.toString(), result.canImport)
        assertTrue(result.issues.toString(), result.issues.isEmpty())
        assertNoForbiddenSourceIdentifiers(encodedFixture)
    }

    @Test
    fun anonymizedFullDomainFixtureImportsWithLocalIdsOnly() = runTest {
        val backup = MoneyTrackerBackupV1Json.decodeFromString(fixture(FULL_DOMAIN_FIXTURE))
        val store = FixtureImportStore()

        val result = MoneyTrackerBackupImporter(store).importBackup(backup)

        assertEquals(1, store.transactionsStarted)
        assertEquals(1, result.importedProfileCount)
        assertEquals(2, result.importedExchangeRateSnapshots)
        assertEquals(
            MoneyTrackerBackupImportedProfileCounts(
                accounts = 5,
                categories = 6,
                transactions = 5,
                transfers = 1,
                budgets = 2,
                recurringTransactions = 2,
                savingsGoals = 2,
                goalTransactions = 2,
                exchangeRateOverrides = 1,
                transactionTemplates = 2,
            ),
            result.importedProfiles.single().counts,
        )

        val accountIds = store.accounts.associate { it.name to it.id }
        val categoryIds = store.categories.associate { it.name to it.id }
        val transactionIds = store.transactions.associate { it.note to it.id }
        val goalIds = store.savingsGoals.associate { it.name to it.id }

        val transfer = store.transfers.single()
        assertEquals(accountIds.getValue("Main wallet"), transfer.fromAccountId)
        assertEquals(accountIds.getValue("Travel reserve"), transfer.toAccountId)
        assertEquals(transactionIds.getValue("Move to reserve"), transfer.fromTransactionId)
        assertEquals(transactionIds.getValue("Reserve received"), transfer.toTransactionId)

        assertTrue(store.budgets.all { it.categoryId in categoryIds.values })
        assertEquals(accountIds.getValue("Credit card"), store.recurringTransactions.first().accountId)
        assertEquals(accountIds.getValue("Travel reserve"), store.savingsGoals.first().accountId)
        assertEquals(goalIds.getValue("Trip fund"), store.goalTransactions.first().goalId)
        assertEquals(accountIds.getValue("Main wallet"), store.transactionTemplates.first().accountId)

        assertEquals(SystemCategoryLocalization.SALARY, store.categories.single { it.name == "Salary" }.localizationKey)
        assertEquals(SystemCategoryLocalization.TRANSFER, store.categories.single { it.name == "Transfer" }.localizationKey)
        assertEquals(SystemCategoryLocalization.ADJUSTMENT, store.categories.single { it.name == "Adjustment" }.localizationKey)
        assertEquals(null, store.categories.single { it.name == "Dining" }.localizationKey)
        assertEquals(null, store.categories.single { it.name == "General" }.localizationKey)

        val persistedText = store.persistedText()
        assertTrue(
            "Import store persisted export-local refs: $persistedText",
            persistedText.none { exportLocalRefPattern.containsMatchIn(it) },
        )
    }

    private fun assertFullDomainCoverage(backup: MoneyTrackerBackup) {
        assertEquals(MONEY_TRACKER_BACKUP_FORMAT, backup.format)
        assertEquals(MONEY_TRACKER_BACKUP_VERSION, backup.version)
        assertEquals(1, backup.profiles.size)
        assertEquals(2, backup.exchangeRateSnapshots.size)

        val profile = backup.profiles.single()
        assertEquals("profile:p000001", profile.ref)
        assertEquals(listOf("USD", "EUR", "RUB"), profile.displayCurrencyCodes)
        assertEquals(BackupStatsChartStyle.StackedBar, profile.uiPreferences.statsChartStyle)
        assertEquals(true, profile.uiPreferences.animateNumbers)
        assertEquals(BackupTheme.Dark, profile.uiPreferences.theme)
        assertTrue(profile.uiPreferences.hideAmounts)
        assertTrue(profile.notificationPreferences.notifyBudgetAlerts)
        assertTrue(profile.notificationPreferences.notifyRecurringReminders)
        assertFalse(profile.notificationPreferences.notifyWeeklySummary)
        assertTrue(profile.notificationPreferences.notifyGoalMilestones)

        assertEquals(
            setOf(
                BackupAccountType.Checking,
                BackupAccountType.Cash,
                BackupAccountType.Savings,
                BackupAccountType.Credit,
                BackupAccountType.Crypto,
            ),
            profile.accounts.map { it.type }.toSet(),
        )
        assertEquals(
            setOf(
                BackupCategoryType.Expense,
                BackupCategoryType.Income,
                BackupCategoryType.Both,
                BackupCategoryType.Savings,
                BackupCategoryType.Transfer,
                BackupCategoryType.Adjustment,
            ),
            profile.categories.map { it.type }.toSet(),
        )
        assertEquals(5, profile.transactions.size)
        assertTrue(profile.transactions.any { it.isAdjustment })
        assertEquals(1, profile.transfers.size)
        assertEquals(2, profile.budgets.size)
        assertEquals(setOf(BackupBudgetPeriod.Monthly, BackupBudgetPeriod.Weekly), profile.budgets.map { it.period }.toSet())
        assertEquals(2, profile.recurringTransactions.size)
        assertEquals(
            setOf(BackupRecurringFrequency.Monthly, BackupRecurringFrequency.Weekly),
            profile.recurringTransactions.map { it.frequency }.toSet(),
        )
        assertEquals(2, profile.savingsGoals.size)
        assertTrue(profile.savingsGoals.any { it.deadlineDate == null && it.accountRef == null })
        assertEquals(
            setOf(BackupGoalTransactionType.Deposit, BackupGoalTransactionType.Withdraw),
            profile.goalTransactions.map { it.type }.toSet(),
        )
        assertEquals(1, profile.exchangeRateOverrides.size)
        assertEquals(2, profile.transactionTemplates.size)
        assertEquals(setOf(true, false), profile.transactionTemplates.map { it.amountFixed }.toSet())
    }

    private fun assertNoForbiddenSourceIdentifiers(encodedFixture: String) {
        val root = MoneyTrackerBackupV1Json.json.parseToJsonElement(encodedFixture)
        val keys = collectJsonKeys(root)
        val forbiddenKeys = keys.filter(::isForbiddenIdentityKey)
        assertTrue("Forbidden source identifier keys: $forbiddenKeys", forbiddenKeys.isEmpty())

        forbiddenRawFragments.forEach { fragment ->
            assertFalse(
                "Fixture must not contain raw source/device fragment '$fragment'.",
                encodedFixture.contains(fragment, ignoreCase = true),
            )
        }

        val forbiddenStrings = collectJsonStrings(root).filter(::isForbiddenIdentityValue)
        assertTrue("Forbidden source identifier values: $forbiddenStrings", forbiddenStrings.isEmpty())
    }

    private fun collectJsonKeys(element: JsonElement): Set<String> {
        return when (element) {
            is JsonObject -> element.keys + element.values.flatMap { collectJsonKeys(it) }
            is JsonArray -> element.flatMap { collectJsonKeys(it) }.toSet()
            is JsonPrimitive -> emptySet()
        }
    }

    private fun collectJsonStrings(element: JsonElement): List<String> {
        return when (element) {
            is JsonObject -> element.values.flatMap(::collectJsonStrings)
            is JsonArray -> element.flatMap(::collectJsonStrings)
            is JsonPrimitive -> if (element.isString) listOf(element.contentOrNull.orEmpty()) else emptyList()
        }
    }

    private fun isForbiddenIdentityKey(key: String): Boolean {
        val normalized = key.normalizeIdentityToken()
        if (normalized == "id" || normalized.endsWith("_id")) {
            return true
        }
        if (normalized in exactForbiddenIdentityKeys) {
            return true
        }
        return forbiddenIdentityParts.any { part ->
            normalized == part ||
                normalized.startsWith("${part}_") ||
                normalized.endsWith("_$part") ||
                normalized.contains("_${part}_")
        }
    }

    private fun isForbiddenIdentityValue(value: String): Boolean {
        val normalized = value.lowercase()
        return forbiddenValueFragments.any { fragment -> fragment in normalized } ||
            botOrChatMetadataPattern.containsMatchIn(normalized)
    }

    private fun fixture(name: String): String {
        return requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) {
            "Missing fixture $name"
        }.readText()
    }

    private fun String.normalizedLineEndings(): String = replace("\r\n", "\n")

    private fun String.normalizeIdentityToken(): String {
        return replace(camelBoundary, "$1_$2")
            .replace('-', '_')
            .lowercase()
    }

    private companion object {
        const val FULL_DOMAIN_FIXTURE = "money_tracker_backup_v1_anonymized_full.json"
        val camelBoundary = Regex("([a-z0-9])([A-Z])")
        val exportLocalRefPattern = Regex(
            """\b(?:profile|account|category|transaction|transfer|budget|recurring|goal|goal-transaction|rate-override|template):[a-z0-9_.:-]+""",
        )
        val botOrChatMetadataPattern = Regex("""(^|[^a-z0-9])(bot|chat)([^a-z0-9]|$)""")
        val exactForbiddenIdentityKeys = setOf(
            "telegram",
            "telegram_id",
            "telegram_user_id",
            "telegram_username",
            "username",
            "first_name",
            "last_name",
            "init_data",
            "init_data_unsafe",
            "auth_date",
            "hash",
            "query_id",
            "bot",
            "bot_id",
            "chat",
            "chat_id",
            "legacy",
            "legacy_id",
            "source",
            "source_id",
            "source_db_id",
        )
        val forbiddenIdentityParts = listOf("telegram", "init_data", "bot", "chat", "legacy", "source")
        val forbiddenValueFragments = listOf(
            "telegram",
            "initdata",
            "init_data",
            "legacy",
            "source",
            "sqlcipher",
            "passphrase",
            "keystore",
            "device-bound",
            "device_bound",
            "device bound",
            "secret",
            "password",
        )
        val forbiddenRawFragments = listOf(
            "4242424242",
            "987654321",
            "81001",
            "82001",
            "83001",
            "84001",
            "85001",
            "86001",
            "87001",
            "88001",
            "89001",
            "account:501",
            "transaction:701",
            "profile:901",
            "from_tx_id",
            "to_tx_id",
        )
    }
}

private class FixtureImportStore : MoneyTrackerBackupImportStore {
    var transactionsStarted = 0
        private set
    private var nextId = 1L

    val profiles = mutableListOf<ImportedProfile>()
    val accounts = mutableListOf<ImportedAccount>()
    val categories = mutableListOf<ImportedCategory>()
    val transactions = mutableListOf<ImportedTransaction>()
    val transfers = mutableListOf<ImportedTransfer>()
    val budgets = mutableListOf<ImportedBudget>()
    val recurringTransactions = mutableListOf<ImportedRecurringTransaction>()
    val savingsGoals = mutableListOf<ImportedSavingsGoal>()
    val goalTransactions = mutableListOf<ImportedGoalTransaction>()
    val exchangeRateSnapshots = mutableListOf<ImportedExchangeRateSnapshot>()
    val exchangeRateOverrides = mutableListOf<ImportedExchangeRateOverride>()
    val transactionTemplates = mutableListOf<ImportedTransactionTemplate>()

    override suspend fun <T> withTransaction(block: suspend MoneyTrackerBackupImportStore.() -> T): T {
        transactionsStarted += 1
        return this.block()
    }

    override suspend fun insertProfile(profile: BackupProfile): Long {
        val id = nextId()
        profiles += ImportedProfile(id = id, label = profile.label, languageCode = profile.languageCode)
        return id
    }

    override suspend fun insertAccount(profileId: Long, account: BackupAccount): Long {
        val id = nextId()
        accounts += ImportedAccount(id = id, profileId = profileId, name = account.name)
        return id
    }

    override suspend fun insertCategory(profileId: Long, category: BackupCategory): Long {
        val id = nextId()
        categories += ImportedCategory(
            id = id,
            profileId = profileId,
            name = category.name,
            localizationKey = category.localizationKey,
        )
        return id
    }

    override suspend fun insertTransaction(
        profileId: Long,
        transaction: BackupTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        val id = nextId()
        transactions += ImportedTransaction(
            id = id,
            profileId = profileId,
            accountId = accountId,
            categoryId = categoryId,
            note = transaction.note,
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
        transfers += ImportedTransfer(
            id = id,
            profileId = profileId,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            fromTransactionId = fromTransactionId,
            toTransactionId = toTransactionId,
            note = transfer.note,
        )
        return id
    }

    override suspend fun insertBudget(profileId: Long, budget: BackupBudget, categoryId: Long): Long {
        val id = nextId()
        budgets += ImportedBudget(id = id, profileId = profileId, categoryId = categoryId)
        return id
    }

    override suspend fun insertRecurringTransaction(
        profileId: Long,
        recurring: BackupRecurringTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        val id = nextId()
        recurringTransactions += ImportedRecurringTransaction(
            id = id,
            profileId = profileId,
            accountId = accountId,
            categoryId = categoryId,
            note = recurring.note,
        )
        return id
    }

    override suspend fun insertSavingsGoal(profileId: Long, goal: BackupSavingsGoal, accountId: Long?): Long {
        val id = nextId()
        savingsGoals += ImportedSavingsGoal(id = id, profileId = profileId, accountId = accountId, name = goal.name)
        return id
    }

    override suspend fun insertGoalTransaction(
        profileId: Long,
        transaction: BackupGoalTransaction,
        goalId: Long,
    ): Long {
        val id = nextId()
        goalTransactions += ImportedGoalTransaction(id = id, profileId = profileId, goalId = goalId)
        return id
    }

    override suspend fun saveExchangeRateSnapshot(snapshot: BackupExchangeRateSnapshot): Long {
        val id = nextId()
        exchangeRateSnapshots += ImportedExchangeRateSnapshot(id = id, snapshotDate = snapshot.snapshotDate)
        return id
    }

    override suspend fun saveExchangeRateOverride(profileId: Long, override: BackupExchangeRateOverride): Long {
        val id = nextId()
        exchangeRateOverrides += ImportedExchangeRateOverride(id = id, profileId = profileId)
        return id
    }

    override suspend fun insertTransactionTemplate(
        profileId: Long,
        template: BackupTransactionTemplate,
        accountId: Long,
        categoryId: Long,
    ): Long {
        val id = nextId()
        transactionTemplates += ImportedTransactionTemplate(
            id = id,
            profileId = profileId,
            accountId = accountId,
            categoryId = categoryId,
            name = template.name,
            note = template.note,
        )
        return id
    }

    fun persistedText(): List<String> {
        return profiles.flatMap { listOf(it.label, it.languageCode) } +
            accounts.map { it.name } +
            categories.map { it.name } +
            transactions.map { it.note } +
            transfers.map { it.note } +
            recurringTransactions.map { it.note } +
            savingsGoals.map { it.name } +
            exchangeRateSnapshots.map { it.snapshotDate } +
            transactionTemplates.flatMap { listOf(it.name, it.note) }
    }

    private fun nextId(): Long = nextId++
}

private data class ImportedProfile(val id: Long, val label: String, val languageCode: String)
private data class ImportedAccount(val id: Long, val profileId: Long, val name: String)
private data class ImportedCategory(
    val id: Long,
    val profileId: Long,
    val name: String,
    val localizationKey: String?,
)
private data class ImportedTransaction(
    val id: Long,
    val profileId: Long,
    val accountId: Long,
    val categoryId: Long,
    val note: String,
)
private data class ImportedTransfer(
    val id: Long,
    val profileId: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val fromTransactionId: Long?,
    val toTransactionId: Long?,
    val note: String,
)
private data class ImportedBudget(val id: Long, val profileId: Long, val categoryId: Long)
private data class ImportedRecurringTransaction(
    val id: Long,
    val profileId: Long,
    val accountId: Long,
    val categoryId: Long,
    val note: String,
)
private data class ImportedSavingsGoal(val id: Long, val profileId: Long, val accountId: Long?, val name: String)
private data class ImportedGoalTransaction(val id: Long, val profileId: Long, val goalId: Long)
private data class ImportedExchangeRateSnapshot(val id: Long, val snapshotDate: String)
private data class ImportedExchangeRateOverride(val id: Long, val profileId: Long)
private data class ImportedTransactionTemplate(
    val id: Long,
    val profileId: Long,
    val accountId: Long,
    val categoryId: Long,
    val name: String,
    val note: String,
)
