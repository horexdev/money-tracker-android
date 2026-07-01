package dev.horex.moneytracker.core.currency

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
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
            )

            val profileRate = repository.getRate(profileId, "USD", "EUR", "2026-04-02")
            val otherProfileRate = repository.getRate(otherProfileId, "USD", "EUR", "2026-04-02")

            assertEquals(ExchangeRateSource.ManualOverride, profileRate.source)
            assertEquals(95_000_000L, profileRate.rateE8)
            assertEquals(ExchangeRateSource.Snapshot, otherProfileRate.source)
            assertEquals(92_000_000L, otherProfileRate.rateE8)

            now = 30L
            val updated = repository.saveManualOverride(
                profileId = profileId,
                input = SaveExchangeRateOverrideInput(
                    effectiveDate = "2026-04-01",
                    baseCurrency = "USD",
                    targetCurrency = "EUR",
                    rateE8 = 96_000_000L,
                ),
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
            assertFailsWithType<ExchangeRateNotFoundException> {
                repository.getRate(profileId, "USD", "EUR", "2026-04-01")
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
    ): Long {
        return database.localProfileDao().insert(
            LocalProfileEntity(
                label = label,
                languageCode = "en",
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
