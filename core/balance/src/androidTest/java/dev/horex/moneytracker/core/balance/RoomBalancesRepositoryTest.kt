package dev.horex.moneytracker.core.balance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.currency.RoomCurrencyRatesRepository
import dev.horex.moneytracker.core.currency.SaveExchangeRateSnapshotInput
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBalancesRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE_NAME)
        context.getSharedPreferences(TEST_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun getBalanceDerivesAccountCurrencyAndBaseTotalsFromFixtures() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database, hideAmounts = true)
            val usdAccountId = insertAccount(database, profileId, "Main", "USD", isDefault = true)
            val eurAccountId = insertAccount(database, profileId, "Euro", "EUR")
            val hiddenUsdAccountId = insertAccount(
                database,
                profileId,
                name = "Hidden",
                currency = "USD",
                includeInTotal = false,
            )
            val incomeCategoryId = insertCategory(database, profileId, "Income", "income")
            val expenseCategoryId = insertCategory(database, profileId, "Expense", "expense")
            RoomCurrencyRatesRepository(database).saveSnapshot(
                SaveExchangeRateSnapshotInput(
                    snapshotDate = "2026-01-01",
                    baseCurrency = "EUR",
                    targetCurrency = "USD",
                    rateE8 = 120_000_000,
                ),
            )
            RoomCurrencyRatesRepository(database).saveSnapshot(
                SaveExchangeRateSnapshotInput(
                    snapshotDate = "2026-01-02",
                    baseCurrency = "USD",
                    targetCurrency = "EUR",
                    rateE8 = 90_000_000,
                ),
            )
            insertTransaction(database, profileId, usdAccountId, incomeCategoryId, "income", 20_000, "USD")
            insertTransaction(database, profileId, usdAccountId, expenseCategoryId, "expense", 5_000, "USD")
            insertTransaction(
                database,
                profileId,
                usdAccountId,
                incomeCategoryId,
                "income",
                500,
                "USD",
                isAdjustment = true,
            )
            insertTransaction(database, profileId, eurAccountId, incomeCategoryId, "income", 10_000, "EUR")
            insertTransaction(database, profileId, eurAccountId, expenseCategoryId, "expense", 2_500, "EUR")
            insertTransaction(database, profileId, hiddenUsdAccountId, incomeCategoryId, "income", 99_900, "USD")
            val repository = RoomBalancesRepository(database)

            val snapshot = repository.getBalance(
                profileId,
                BalanceQuery(displayCurrencyCodes = listOf("EUR", "EUR"), displayConversionDate = "2026-01-02"),
            )

            assertEquals("USD", snapshot.baseCurrencyCode)
            assertEquals(24_500L, snapshot.totalInBaseCents)
            assertEquals(listOf("EUR", "USD"), snapshot.byCurrency.map { it.currencyCode })
            assertEquals(10_000L, snapshot.byCurrency.single { it.currencyCode == "EUR" }.incomeCents)
            assertEquals(2_500L, snapshot.byCurrency.single { it.currencyCode == "EUR" }.expenseCents)
            assertEquals(7_500L, snapshot.byCurrency.single { it.currencyCode == "EUR" }.netCents)
            assertEquals(20_500L, snapshot.byCurrency.single { it.currencyCode == "USD" }.incomeCents)
            assertEquals(5_000L, snapshot.byCurrency.single { it.currencyCode == "USD" }.expenseCents)
            assertEquals(15_500L, snapshot.byCurrency.single { it.currencyCode == "USD" }.netCents)
            assertEquals(22_050L, snapshot.displayConversions.single().netCents)
            assertEquals(15_500L, snapshot.accountBalances.single { it.id == usdAccountId }.balanceCents)
            assertEquals(7_500L, snapshot.accountBalances.single { it.id == eurAccountId }.balanceCents)
            assertEquals(99_900L, snapshot.accountBalances.single { it.id == hiddenUsdAccountId }.balanceCents)
            assertEquals(false, snapshot.accountBalances.single { it.id == hiddenUsdAccountId }.includeInTotal)
        } finally {
            database.close()
        }
    }

    @Test
    fun includeExcludedAccountsOptInAddsHiddenAccountsToTotals() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val visibleAccountId = insertAccount(database, profileId, "Visible", "USD", isDefault = true)
            val hiddenAccountId = insertAccount(
                database,
                profileId,
                name = "Hidden",
                currency = "USD",
                includeInTotal = false,
            )
            val categoryId = insertCategory(database, profileId, "Income", "income")
            insertTransaction(database, profileId, visibleAccountId, categoryId, "income", 1_000, "USD")
            insertTransaction(database, profileId, hiddenAccountId, categoryId, "income", 2_000, "USD")
            val repository = RoomBalancesRepository(database)

            val defaultSnapshot = repository.getBalance(profileId)
            val includingHidden = repository.getBalance(
                profileId,
                BalanceQuery(includeExcludedAccounts = true),
            )
            val hiddenAccountSnapshot = repository.getBalance(
                profileId,
                BalanceQuery(accountId = hiddenAccountId),
            )

            assertEquals(1_000L, defaultSnapshot.totalInBaseCents)
            assertEquals(3_000L, includingHidden.totalInBaseCents)
            assertEquals(2_000L, hiddenAccountSnapshot.totalInBaseCents)
        } finally {
            database.close()
        }
    }

    @Test
    fun getBalanceIsScopedToProfileAndAccount() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val profileAccountId = insertAccount(database, profileId, "Main", "USD", isDefault = true)
            val otherAccountId = insertAccount(database, otherProfileId, "Other", "USD", isDefault = true)
            val categoryId = insertCategory(database, profileId, "Income", "income")
            val otherCategoryId = insertCategory(database, otherProfileId, "Income", "income")
            insertTransaction(database, profileId, profileAccountId, categoryId, "income", 4_000, "USD")
            insertTransaction(database, otherProfileId, otherAccountId, otherCategoryId, "income", 9_000, "USD")
            val repository = RoomBalancesRepository(database)

            val snapshot = repository.getBalance(profileId, BalanceQuery(accountId = profileAccountId))

            assertEquals(profileAccountId, snapshot.accountId)
            assertEquals(4_000L, snapshot.totalInBaseCents)
            assertEquals(1, snapshot.byCurrency.size)
            assertFailsWithType<BalanceAccountNotFoundException> {
                repository.getBalance(profileId, BalanceQuery(accountId = otherAccountId))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun getBalanceReportsInvalidCurrencyAndMissingRate() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val eurAccountId = insertAccount(database, profileId, "Euro", "EUR", isDefault = true)
            val categoryId = insertCategory(database, profileId, "Income", "income")
            insertTransaction(database, profileId, eurAccountId, categoryId, "income", 1_000, "EUR")
            val repository = RoomBalancesRepository(database)

            assertFailsWithType<InvalidBalanceCurrencyException> {
                repository.getBalance(profileId, BalanceQuery(baseCurrencyCode = "BAD!"))
            }
            assertFailsWithType<BalanceExchangeRateNotFoundException> {
                repository.getBalance(profileId, BalanceQuery(baseCurrencyCode = "USD"))
            }
        } finally {
            database.close()
        }
    }

    private fun createDatabase(): MoneyTrackerDatabase {
        return MoneyTrackerDatabaseFactory.createEncrypted(
            context = context,
            passphraseStore = AndroidDatabasePassphraseStore(
                context = context,
                preferencesName = TEST_PREFERENCES_NAME,
                keyAlias = TEST_KEY_ALIAS,
            ),
            databaseName = TEST_DATABASE_NAME,
        )
    }

    private suspend fun insertProfile(
        database: MoneyTrackerDatabase,
        label: String = "Personal",
        hideAmounts: Boolean = false,
    ): Long {
        return database.localProfileDao().insert(
            LocalProfileEntity(
                label = label,
                languageCode = "en",
                hideAmounts = hideAmounts,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    private suspend fun insertAccount(
        database: MoneyTrackerDatabase,
        profileId: Long,
        name: String,
        currency: String,
        isDefault: Boolean = false,
        includeInTotal: Boolean = true,
    ): Long {
        return database.accountDao().insert(
            AccountEntity(
                profileId = profileId,
                name = name,
                currencyCode = currency,
                isDefault = isDefault,
                includeInTotal = includeInTotal,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    private suspend fun insertCategory(
        database: MoneyTrackerDatabase,
        profileId: Long,
        name: String,
        type: String,
    ): Long {
        return database.categoryDao().insert(
            CategoryEntity(
                profileId = profileId,
                name = name,
                type = type,
                color = "#64748b",
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    private suspend fun insertTransaction(
        database: MoneyTrackerDatabase,
        profileId: Long,
        accountId: Long,
        categoryId: Long,
        type: String,
        amountCents: Long,
        currencyCode: String,
        isAdjustment: Boolean = false,
    ): Long {
        return database.transactionDao().insert(
            TransactionEntity(
                profileId = profileId,
                type = type,
                amountCents = amountCents,
                categoryId = categoryId,
                accountId = accountId,
                note = "",
                currencyCode = currencyCode,
                snapshotDate = "2026-01-01",
                isAdjustment = isAdjustment,
                createdAtEpochMillis = 1L,
            ),
        )
    }

    private suspend inline fun <reified T : Throwable> assertFailsWithType(
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            assertTrue(
                "Expected ${T::class.java.simpleName}, got ${error::class.java.simpleName}",
                error is T,
            )
        }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "money-tracker-balances-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_balances_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_balances_test_key"
    }
}
