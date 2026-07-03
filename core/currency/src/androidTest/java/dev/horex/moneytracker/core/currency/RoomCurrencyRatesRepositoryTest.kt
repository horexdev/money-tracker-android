package dev.horex.moneytracker.core.currency

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.BudgetEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionEntity
import dev.horex.moneytracker.core.database.model.SavingsGoalEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.model.TransactionTemplateEntity
import dev.horex.moneytracker.core.database.model.TransferEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCurrencyRatesRepositoryTest {
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
    fun saveSnapshotsPersistsSeedRatesAndSupportsLatestLookup() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            insertAccount(database, profileId, "USD")
            insertAccount(database, profileId, "EUR")
            val repository = RoomCurrencyRatesRepository(database, clock = { 10L })

            val saved = repository.saveSnapshots(
                listOf(
                    SaveExchangeRateSnapshotInput(
                        snapshotDate = "2026-04-01",
                        baseCurrency = "usd",
                        targetCurrency = "eur",
                        rateE8 = 92_000_000L,
                    ),
                    SaveExchangeRateSnapshotInput(
                        snapshotDate = "2026-04-02",
                        baseCurrency = "USD",
                        targetCurrency = "EUR",
                        rateE8 = 93_000_000L,
                    ),
                ),
            )

            val exact = repository.getSnapshot("USD", "EUR", "2026-04-01")
            val latest = repository.getLatestSnapshotAtOrBefore("USD", "EUR", "2026-04-03")

            assertEquals(2, saved.size)
            assertEquals(92_000_000L, exact?.rateE8)
            assertEquals("2026-04-02", latest?.snapshotDate)
            assertEquals(93_000_000L, latest?.rateE8)
            assertEquals("2026-04-02", repository.getLatestSnapshotDate())
            assertEquals(listOf("EUR", "USD"), repository.listBaseCurrencies(profileId))
        } finally {
            database.close()
        }
    }

    @Test
    fun manualOverrideTakesPriorityOverSnapshotWithinProfile() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            var now = 20L
            val repository = RoomCurrencyRatesRepository(database, clock = { now })

            repository.saveSnapshot(
                SaveExchangeRateSnapshotInput(
                    snapshotDate = "2026-04-01",
                    baseCurrency = "USD",
                    targetCurrency = "EUR",
                    rateE8 = 92_000_000L,
                ),
            )
            val override = repository.saveManualOverride(
                profileId = profileId,
                input = SaveExchangeRateOverrideInput(
                    effectiveDate = "2026-04-01",
                    baseCurrency = "USD",
                    targetCurrency = "EUR",
                    rateE8 = 95_000_000L,
                ),
                overwriteExisting = false,
            )

            val profileRate = repository.getRate(profileId, "USD", "EUR", "2026-04-02")
            val otherProfileRate = repository.getRate(otherProfileId, "USD", "EUR", "2026-04-02")

            assertEquals(ExchangeRateSource.ManualOverride, profileRate.source)
            assertEquals(95_000_000L, profileRate.rateE8)
            assertEquals(ExchangeRateSource.Snapshot, otherProfileRate.source)
            assertEquals(92_000_000L, otherProfileRate.rateE8)

            now = 30L
            val replacementInput = SaveExchangeRateOverrideInput(
                effectiveDate = "2026-04-01",
                baseCurrency = "USD",
                targetCurrency = "EUR",
                rateE8 = 96_000_000L,
            )
            assertFailsWithType<ExchangeRateOverrideAlreadyExistsException> {
                repository.saveManualOverride(
                    profileId = profileId,
                    input = replacementInput,
                    overwriteExisting = false,
                )
            }
            val updated = repository.saveManualOverride(
                profileId = profileId,
                input = replacementInput,
                overwriteExisting = true,
            )

            assertEquals(override.id, updated.id)
            assertEquals(20L, updated.createdAtEpochMillis)
            assertEquals(30L, updated.updatedAtEpochMillis)
            assertEquals(1, repository.listManualOverrides(profileId).size)
            assertEquals(96_000_000L, repository.getRate(profileId, "USD", "EUR", "2026-04-02").rateE8)
        } finally {
            database.close()
        }
    }

    @Test
    fun sameCurrencyAndValidationRulesAreEnforced() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomCurrencyRatesRepository(database)

            val sameCurrency = repository.getRate(profileId, "usd", "USD", "2026-04-02")

            assertEquals(RATE_SCALE_E8, sameCurrency.rateE8)
            assertEquals(ExchangeRateSource.SameCurrency, sameCurrency.source)
            assertNull(repository.getSnapshot("USD", "EUR", "2026-04-01"))
            assertFailsWithType<InvalidCurrencyCodeException> {
                repository.getRate(profileId, "XYZ", "EUR", "2026-04-01")
            }
            assertFailsWithType<InvalidExchangeRateDateException> {
                repository.getRate(profileId, "USD", "EUR", "04-01-2026")
            }
            assertFailsWithType<InvalidExchangeRateException> {
                repository.saveSnapshot(
                    SaveExchangeRateSnapshotInput(
                        snapshotDate = "2026-04-01",
                        baseCurrency = "USD",
                        targetCurrency = "EUR",
                        rateE8 = 0,
                    ),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun systemRateFallbackResolvesSupportedPairsWithoutPersistedSnapshots() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomCurrencyRatesRepository(database)

            val fallback = repository.getRate(profileId, "USD", "TJS", "2026-04-01")
            val inverse = repository.getRate(profileId, "TJS", "USD", "2026-04-01")

            assertEquals(ExchangeRateSource.SystemRate, fallback.source)
            assertEquals("2026-04-01", fallback.effectiveDate)
            assertTrue(fallback.rateE8 > RATE_SCALE_E8)
            assertEquals(ExchangeRateSource.SystemRate, inverse.source)
            assertTrue(inverse.rateE8 > 0)
            assertNull(repository.getSnapshot("USD", "TJS", "2026-04-01"))
        } finally {
            database.close()
        }
    }

    @Test
    fun savedSnapshotTakesPriorityOverSystemRateFallback() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomCurrencyRatesRepository(database)

            repository.saveSnapshot(
                SaveExchangeRateSnapshotInput(
                    snapshotDate = "2026-04-01",
                    baseCurrency = "USD",
                    targetCurrency = "TJS",
                    rateE8 = 1_100_000_000L,
                ),
            )

            val resolved = repository.getRate(profileId, "USD", "TJS", "2026-04-02")

            assertEquals(ExchangeRateSource.Snapshot, resolved.source)
            assertEquals(1_100_000_000L, resolved.rateE8)
        } finally {
            database.close()
        }
    }

    @Test
    fun listActiveCurrencyCodesUsesOnlyProfileDataAndIgnoresSnapshotCatalog() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database, displayCurrenciesCsv = "TJS, GBP")
            val otherProfileId = insertProfile(database, label = "Other", displayCurrenciesCsv = "CAD")
            val repository = RoomCurrencyRatesRepository(database)
            val accountUsd = insertAccount(database, profileId, "USD")
            val accountEur = insertAccount(database, profileId, "EUR")
            insertAccount(database, otherProfileId, "CAD")
            val categoryId = insertCategory(database, profileId)

            database.transactionDao().insert(
                TransactionEntity(
                    profileId = profileId,
                    type = "expense",
                    amountCents = 100,
                    categoryId = categoryId,
                    accountId = accountUsd,
                    currencyCode = "RUB",
                    snapshotDate = "2026-04-01",
                    createdAtEpochMillis = 1L,
                ),
            )
            database.transferDao().insert(
                TransferEntity(
                    profileId = profileId,
                    fromAccountId = accountUsd,
                    toAccountId = accountEur,
                    amountCents = 100,
                    fromCurrencyCode = "USD",
                    toCurrencyCode = "EUR",
                    exchangeRateE8 = 90_000_000L,
                    createdAtEpochMillis = 2L,
                ),
            )
            database.budgetDao().insert(
                BudgetEntity(
                    profileId = profileId,
                    categoryId = categoryId,
                    limitCents = 1_000,
                    period = "monthly",
                    currencyCode = "UAH",
                    createdAtEpochMillis = 3L,
                    updatedAtEpochMillis = 3L,
                ),
            )
            database.recurringTransactionDao().insert(
                RecurringTransactionEntity(
                    profileId = profileId,
                    accountId = accountUsd,
                    categoryId = categoryId,
                    type = "income",
                    amountCents = 500,
                    currencyCode = "KZT",
                    frequency = "monthly",
                    nextRunAtEpochMillis = 4L,
                    createdAtEpochMillis = 4L,
                    updatedAtEpochMillis = 4L,
                ),
            )
            database.savingsGoalDao().insert(
                SavingsGoalEntity(
                    profileId = profileId,
                    name = "Goal",
                    targetCents = 10_000,
                    currencyCode = "UZS",
                    createdAtEpochMillis = 5L,
                    updatedAtEpochMillis = 5L,
                ),
            )
            database.transactionTemplateDao().insert(
                TransactionTemplateEntity(
                    profileId = profileId,
                    name = "Template",
                    type = "expense",
                    amountCents = 250,
                    categoryId = categoryId,
                    accountId = accountUsd,
                    currencyCode = "BRL",
                    createdAtEpochMillis = 6L,
                    updatedAtEpochMillis = 6L,
                ),
            )
            repository.saveManualOverride(
                profileId = profileId,
                input = SaveExchangeRateOverrideInput(
                    effectiveDate = "2026-04-01",
                    baseCurrency = "SAR",
                    targetCurrency = "TRY",
                    rateE8 = 1_200_000_000L,
                ),
            )
            repository.saveSnapshot(
                SaveExchangeRateSnapshotInput(
                    snapshotDate = "2026-04-01",
                    baseCurrency = "USD",
                    targetCurrency = "JPY",
                    rateE8 = 15_000_000_000L,
                ),
            )

            val activeCurrencies = repository.listActiveCurrencyCodes(profileId)

            assertEquals(
                listOf("BRL", "EUR", "GBP", "KZT", "RUB", "SAR", "TJS", "TRY", "UAH", "USD", "UZS"),
                activeCurrencies,
            )
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
        displayCurrenciesCsv: String = "",
    ): Long {
        return database.localProfileDao().insert(
            LocalProfileEntity(
                label = label,
                languageCode = "en",
                displayCurrenciesCsv = displayCurrenciesCsv,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    private suspend fun insertAccount(
        database: MoneyTrackerDatabase,
        profileId: Long,
        currencyCode: String,
    ): Long {
        return database.accountDao().insert(
            AccountEntity(
                profileId = profileId,
                name = "Account-$currencyCode-${System.nanoTime()}",
                currencyCode = currencyCode,
                isDefault = true,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    private suspend fun insertCategory(
        database: MoneyTrackerDatabase,
        profileId: Long,
    ): Long {
        return database.categoryDao().insert(
            CategoryEntity(
                profileId = profileId,
                name = "Category-${System.nanoTime()}",
                type = "expense",
                updatedAtEpochMillis = 1L,
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
        const val TEST_DATABASE_NAME = "money-tracker-currency-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_currency_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_currency_test_key"
    }
}
