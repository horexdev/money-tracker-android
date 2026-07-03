package dev.horex.moneytracker.core.backup

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MoneyTrackerSqlDumpConverterRehearsalTest {
    @Test
    fun syntheticSqlDumpConverterFixtureValidatesImportsAndKeepsExpectedTotals() = runTest {
        val encoded = fixture(SYNTHETIC_CONVERTER_FIXTURE)

        val validation = MoneyTrackerBackupV1Validator.validateJson(encoded)

        assertTrue(validation.issues.toString(), validation.canImport)
        assertTrue(validation.issues.toString(), validation.issues.isEmpty())
        assertFalse(encoded.contains("1001"))
        assertFalse(encoded.contains("demo_user", ignoreCase = true))
        assertFalse(encoded.contains("Demo"))
        assertFalse(encoded.contains("Person"))

        val backup = MoneyTrackerBackupV1Json.decodeFromString(encoded)
        val profile = backup.profiles.single()

        assertEquals(2, backup.exchangeRateSnapshots.size)
        assertEquals(2, profile.accounts.size)
        assertEquals(3, profile.categories.size)
        assertEquals(4, profile.transactions.size)
        assertEquals(1, profile.transfers.size)
        assertEquals(1, profile.budgets.size)
        assertEquals(1, profile.recurringTransactions.size)
        assertEquals(1, profile.savingsGoals.size)
        assertEquals(1, profile.goalTransactions.size)
        assertEquals(1, profile.transactionTemplates.size)

        assertEquals(
            mapOf(
                "EUR" to AmountTotals(incomeCents = 46_000, expenseCents = 0, netCents = 46_000),
                "USD" to AmountTotals(incomeCents = 250_000, expenseCents = 52_750, netCents = 197_250),
            ),
            profile.transactionTotalsByCurrency(),
        )
        assertEquals(mapOf("EUR" to 46_000L, "USD" to 197_250L), profile.includedAccountTotalsByCurrency())
        assertEquals(
            mapOf(
                "USD->EUR" to TransferTotals(
                    transferCount = 1,
                    fromAmountCents = 50_000,
                    toAmountCents = 46_000,
                ),
            ),
            profile.transferTotalsByCurrencyPair(),
        )
        assertEquals(mapOf("USD" to 75_000L), profile.budgetLimitsByCurrency())
        assertEquals(
            mapOf("USD" to AmountTotals(incomeCents = 0, expenseCents = 1_500, netCents = -1_500)),
            profile.recurringTotalsByCurrency(),
        )
        assertEquals(mapOf("EUR" to 46_000L), profile.savingsCurrentByCurrency())
        assertEquals(
            mapOf("EUR" to AmountTotals(incomeCents = 46_000, expenseCents = 0, netCents = 46_000)),
            profile.goalTransactionTotalsByCurrency(),
        )

        val importResult = MoneyTrackerBackupImporter(CountingImportStore()).importBackup(backup)
        assertEquals(1, importResult.importedProfileCount)
        assertEquals(2, importResult.importedExchangeRateSnapshots)
        assertEquals(
            MoneyTrackerBackupImportedProfileCounts(
                accounts = 2,
                categories = 3,
                transactions = 4,
                transfers = 1,
                budgets = 1,
                recurringTransactions = 1,
                savingsGoals = 1,
                goalTransactions = 1,
                exchangeRateOverrides = 0,
                transactionTemplates = 1,
            ),
            importResult.importedProfiles.single().counts,
        )
    }

    @Test
    fun privateBackupArtifactValidatesAndImportsWhenConfigured() = runTest {
        val backupFile = System.getProperty("moneyTrackerBackupFile")
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
        assumeTrue(
            "Set -DmoneyTrackerBackupFile=<private backup path> to run the private artifact rehearsal.",
            backupFile?.isFile == true,
        )

        val encoded = requireNotNull(backupFile).readText()
        val validation = MoneyTrackerBackupV1Validator.validateJson(encoded)
        assertTrue(validation.issues.toString(), validation.canImport)
        assertTrue(validation.issues.toString(), validation.issues.isEmpty())

        val backup = MoneyTrackerBackupV1Json.decodeFromString(encoded)
        val importResult = MoneyTrackerBackupImporter(CountingImportStore()).importBackup(backup)

        assertEquals(backup.profiles.size, importResult.importedProfileCount)
        backup.profiles.zip(importResult.importedProfiles).forEach { (profile, imported) ->
            assertEquals(profile.accounts.size, imported.counts.accounts)
            assertEquals(profile.categories.size, imported.counts.categories)
            assertEquals(profile.transactions.size, imported.counts.transactions)
            assertEquals(profile.transfers.size, imported.counts.transfers)
            assertEquals(profile.transactionTemplates.size, imported.counts.transactionTemplates)
        }
    }

    private fun BackupProfile.transactionTotalsByCurrency(): Map<String, AmountTotals> {
        return transactions
            .groupingBy { it.currencyCode }
            .aggregateTo(sortedMapOf()) { _, totals: AmountTotals?, transaction, _ ->
                (totals ?: AmountTotals()).plus(transaction.type, transaction.amountCents)
            }
    }

    private fun BackupProfile.includedAccountTotalsByCurrency(): Map<String, Long> {
        val accountsByRef = accounts.associateBy { it.ref }
        val balances = sortedMapOf<String, Long>()
        transactions.forEach { transaction ->
            val account = accountsByRef.getValue(transaction.accountRef)
            if (account.includeInTotal) {
                val signedAmount = when (transaction.type) {
                    BackupTransactionType.Income -> transaction.amountCents
                    BackupTransactionType.Expense -> -transaction.amountCents
                }
                balances[transaction.currencyCode] = balances.getOrDefault(transaction.currencyCode, 0L) + signedAmount
            }
        }
        return balances
    }

    private fun BackupProfile.transferTotalsByCurrencyPair(): Map<String, TransferTotals> {
        val transactionsByRef = transactions.associateBy { it.ref }
        return transfers
            .groupingBy { "${it.fromCurrencyCode}->${it.toCurrencyCode}" }
            .aggregateTo(sortedMapOf()) { _, totals: TransferTotals?, transfer, _ ->
                val toAmount = transfer.toTransactionRef
                    ?.let(transactionsByRef::get)
                    ?.amountCents
                    ?: 0L
                (totals ?: TransferTotals()).plus(transfer.amountCents, toAmount)
            }
    }

    private fun BackupProfile.budgetLimitsByCurrency(): Map<String, Long> {
        return budgets
            .groupingBy { it.currencyCode }
            .foldTo(sortedMapOf(), 0L) { total, budget -> total + budget.limitCents }
    }

    private fun BackupProfile.recurringTotalsByCurrency(): Map<String, AmountTotals> {
        return recurringTransactions
            .groupingBy { it.currencyCode }
            .aggregateTo(sortedMapOf()) { _, totals: AmountTotals?, recurring, _ ->
                (totals ?: AmountTotals()).plus(recurring.type, recurring.amountCents)
            }
    }

    private fun BackupProfile.savingsCurrentByCurrency(): Map<String, Long> {
        return savingsGoals
            .groupingBy { it.currencyCode }
            .foldTo(sortedMapOf(), 0L) { total, goal -> total + goal.currentCents }
    }

    private fun BackupProfile.goalTransactionTotalsByCurrency(): Map<String, AmountTotals> {
        val goalsByRef = savingsGoals.associateBy { it.ref }
        return goalTransactions
            .groupingBy { goalsByRef.getValue(it.goalRef).currencyCode }
            .aggregateTo(sortedMapOf()) { _, totals: AmountTotals?, transaction, _ ->
                val transactionType = when (transaction.type) {
                    BackupGoalTransactionType.Deposit -> BackupTransactionType.Income
                    BackupGoalTransactionType.Withdraw -> BackupTransactionType.Expense
                }
                (totals ?: AmountTotals()).plus(transactionType, transaction.amountCents)
            }
    }

    private fun AmountTotals.plus(type: BackupTransactionType, amountCents: Long): AmountTotals {
        return when (type) {
            BackupTransactionType.Income -> copy(
                incomeCents = incomeCents + amountCents,
                netCents = netCents + amountCents,
            )
            BackupTransactionType.Expense -> copy(
                expenseCents = expenseCents + amountCents,
                netCents = netCents - amountCents,
            )
        }
    }

    private fun TransferTotals.plus(fromAmountCents: Long, toAmountCents: Long): TransferTotals {
        return copy(
            transferCount = transferCount + 1,
            fromAmountCents = this.fromAmountCents + fromAmountCents,
            toAmountCents = this.toAmountCents + toAmountCents,
        )
    }

    private fun fixture(name: String): String {
        return requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) {
            "Missing fixture $name"
        }.readText()
    }

    private data class AmountTotals(
        val incomeCents: Long = 0,
        val expenseCents: Long = 0,
        val netCents: Long = 0,
    )

    private data class TransferTotals(
        val transferCount: Int = 0,
        val fromAmountCents: Long = 0,
        val toAmountCents: Long = 0,
    )

    private companion object {
        const val SYNTHETIC_CONVERTER_FIXTURE = "sql_dump_converter_synthetic_backup_v1.json"
    }
}

private class CountingImportStore : MoneyTrackerBackupImportStore {
    private var nextId = 1L

    override suspend fun <T> withTransaction(block: suspend MoneyTrackerBackupImportStore.() -> T): T {
        return this.block()
    }

    override suspend fun insertProfile(profile: BackupProfile): Long = nextId()

    override suspend fun insertAccount(profileId: Long, account: BackupAccount): Long = nextId()

    override suspend fun insertCategory(profileId: Long, category: BackupCategory): Long = nextId()

    override suspend fun insertTransaction(
        profileId: Long,
        transaction: BackupTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long = nextId()

    override suspend fun insertTransfer(
        profileId: Long,
        transfer: BackupTransfer,
        fromAccountId: Long,
        toAccountId: Long,
        fromTransactionId: Long?,
        toTransactionId: Long?,
    ): Long = nextId()

    override suspend fun insertBudget(profileId: Long, budget: BackupBudget, categoryId: Long): Long = nextId()

    override suspend fun insertRecurringTransaction(
        profileId: Long,
        recurring: BackupRecurringTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long = nextId()

    override suspend fun insertSavingsGoal(profileId: Long, goal: BackupSavingsGoal, accountId: Long?): Long = nextId()

    override suspend fun insertGoalTransaction(
        profileId: Long,
        transaction: BackupGoalTransaction,
        goalId: Long,
    ): Long = nextId()

    override suspend fun saveExchangeRateSnapshot(snapshot: BackupExchangeRateSnapshot): Long = nextId()

    override suspend fun saveExchangeRateOverride(profileId: Long, override: BackupExchangeRateOverride): Long = nextId()

    override suspend fun insertTransactionTemplate(
        profileId: Long,
        template: BackupTransactionTemplate,
        accountId: Long,
        categoryId: Long,
    ): Long = nextId()

    private fun nextId(): Long = nextId++
}
