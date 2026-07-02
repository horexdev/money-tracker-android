package dev.horex.moneytracker.core.stats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.model.TransferEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RoomStatsRepositoryTest {
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
    fun getStatsAggregatesCategoriesForMonthAndKeepsCurrenciesSeparate() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val usdAccountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val eurAccountId = insertAccount(database, profileId, name = "Euro", currency = "EUR")
            val foodCategoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val salaryCategoryId = insertCategory(database, profileId, name = "Salary", type = "income")
            insertTransaction(database, profileId, usdAccountId, foodCategoryId, "expense", 1_000, "USD", JULY_02)
            insertTransaction(database, profileId, usdAccountId, foodCategoryId, "expense", 2_500, "USD", JULY_03)
            insertTransaction(database, profileId, eurAccountId, foodCategoryId, "expense", 700, "EUR", JULY_03)
            insertTransaction(database, profileId, usdAccountId, salaryCategoryId, "income", 20_000, "USD", JULY_04)
            insertTransaction(database, profileId, usdAccountId, foodCategoryId, "expense", 9_999, "USD", AUGUST_01)
            val repository = RoomStatsRepository(
                database = database,
                clock = { JULY_15 },
                zoneId = ZoneOffset.UTC,
            )

            val snapshot = repository.getStats(profileId)

            assertEquals("month", snapshot.period)
            assertEquals(JULY_01, snapshot.range.fromEpochMillisInclusive)
            assertEquals(AUGUST_01, snapshot.range.toEpochMillisExclusive)
            assertEquals(3, snapshot.items.size)
            assertEquals(20_000L, snapshot.items[0].totalCents)
            assertEquals(StatsTransactionType.Income, snapshot.items[0].type)
            assertEquals("Salary", snapshot.items[0].categoryName)
            assertEquals("USD", snapshot.items[0].currencyCode)
            val usdFood = snapshot.items.single { it.categoryName == "Food" && it.currencyCode == "USD" }
            assertEquals(3_500L, usdFood.totalCents)
            assertEquals(2L, usdFood.transactionCount)
            val eurFood = snapshot.items.single { it.categoryName == "Food" && it.currencyCode == "EUR" }
            assertEquals(700L, eurFood.totalCents)
            assertEquals(StatsTransactionType.Expense, eurFood.type)
        } finally {
            database.close()
        }
    }

    @Test
    fun getStatsFiltersByAccountAndCustomRange() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val firstAccountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val secondAccountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val categoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            insertTransaction(database, profileId, firstAccountId, categoryId, "expense", 1_000, "USD", JULY_02)
            insertTransaction(database, profileId, secondAccountId, categoryId, "expense", 2_000, "USD", JULY_03)
            insertTransaction(database, profileId, secondAccountId, categoryId, "expense", 4_000, "USD", JULY_10)
            val repository = RoomStatsRepository(database, clock = { JULY_15 }, zoneId = ZoneOffset.UTC)

            val snapshot = repository.getStats(
                profileId = profileId,
                query = StatsQuery(
                    accountId = secondAccountId,
                    customRange = StatsRange(
                        fromEpochMillisInclusive = JULY_03,
                        toEpochMillisExclusive = JULY_04,
                    ),
                ),
            )

            assertEquals("custom", snapshot.period)
            assertEquals(listOf(2_000L), snapshot.items.map { it.totalCents })
            assertEquals(listOf(1L), snapshot.items.map { it.transactionCount })
        } finally {
            database.close()
        }
    }

    @Test
    fun getStatsExcludesAdjustmentsTransferLinkedTransactionsAndOtherProfiles() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val fromAccountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val toAccountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val otherAccountId = insertAccount(database, otherProfileId, name = "Other", currency = "USD")
            val foodCategoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val transferCategoryId = insertCategory(database, profileId, name = "Transfer", type = "transfer", isProtected = true)
            val otherCategoryId = insertCategory(database, otherProfileId, name = "Food", type = "expense")
            insertTransaction(database, profileId, fromAccountId, foodCategoryId, "expense", 1_500, "USD", JULY_02)
            insertTransaction(
                database = database,
                profileId = profileId,
                accountId = fromAccountId,
                categoryId = foodCategoryId,
                type = "income",
                amountCents = 8_000,
                currencyCode = "USD",
                createdAt = JULY_03,
                isAdjustment = true,
            )
            val transferFromId = insertTransaction(
                database,
                profileId,
                fromAccountId,
                transferCategoryId,
                "expense",
                3_000,
                "USD",
                JULY_04,
            )
            val transferToId = insertTransaction(
                database,
                profileId,
                toAccountId,
                transferCategoryId,
                "income",
                3_000,
                "USD",
                JULY_04,
            )
            database.transferDao().insert(
                TransferEntity(
                    profileId = profileId,
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amountCents = 3_000,
                    fromCurrencyCode = "USD",
                    toCurrencyCode = "USD",
                    fromTransactionId = transferFromId,
                    toTransactionId = transferToId,
                    createdAtEpochMillis = JULY_04,
                ),
            )
            insertTransaction(database, otherProfileId, otherAccountId, otherCategoryId, "expense", 9_000, "USD", JULY_02)
            val repository = RoomStatsRepository(database, clock = { JULY_15 }, zoneId = ZoneOffset.UTC)

            val snapshot = repository.getStats(profileId, StatsQuery(period = StatsPeriod.Month))

            assertEquals(1, snapshot.items.size)
            assertEquals("Food", snapshot.items.single().categoryName)
            assertEquals(1_500L, snapshot.items.single().totalCents)
        } finally {
            database.close()
        }
    }

    @Test
    fun getStatsCalculatesNamedPeriodRanges() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val categoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            insertTransaction(database, profileId, accountId, categoryId, "expense", 100, "USD", JUNE_30)
            insertTransaction(database, profileId, accountId, categoryId, "expense", 200, "USD", JULY_13)
            insertTransaction(database, profileId, accountId, categoryId, "expense", 300, "USD", JULY_15)
            val repository = RoomStatsRepository(database, clock = { JULY_15 }, zoneId = ZoneOffset.UTC)

            val today = repository.getStats(profileId, StatsQuery(period = StatsPeriod.Today))
            val week = repository.getStats(profileId, StatsQuery(period = StatsPeriod.Week))
            val lastMonth = repository.getStats(profileId, StatsQuery(period = StatsPeriod.LastMonth))

            assertEquals(JULY_15, today.range.fromEpochMillisInclusive)
            assertEquals(JULY_16, today.range.toEpochMillisExclusive)
            assertEquals(listOf(300L), today.items.map { it.totalCents })
            assertEquals(JULY_13, week.range.fromEpochMillisInclusive)
            assertEquals(JULY_20, week.range.toEpochMillisExclusive)
            assertEquals(listOf(500L), week.items.map { it.totalCents })
            assertEquals(JUNE_01, lastMonth.range.fromEpochMillisInclusive)
            assertEquals(JULY_01, lastMonth.range.toEpochMillisExclusive)
            assertEquals(listOf(100L), lastMonth.items.map { it.totalCents })
        } finally {
            database.close()
        }
    }

    @Test
    fun getStatsRejectsInvalidCustomRangeAndUnknownAccount() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomStatsRepository(database, clock = { JULY_15 }, zoneId = ZoneOffset.UTC)

            assertFailsWithType<InvalidStatsDateRangeException> {
                repository.getStats(
                    profileId,
                    StatsQuery(customRange = StatsRange(JULY_03, JULY_03)),
                )
            }
            assertFailsWithType<StatsAccountNotFoundException> {
                repository.getStats(profileId, StatsQuery(accountId = 999))
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
        name: String,
        currency: String,
    ): Long {
        return database.accountDao().insert(
            AccountEntity(
                profileId = profileId,
                name = name,
                currencyCode = currency,
                isDefault = true,
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
        isProtected: Boolean = false,
    ): Long {
        return database.categoryDao().insert(
            CategoryEntity(
                profileId = profileId,
                name = name,
                icon = "wallet",
                type = type,
                color = "#64748b",
                isProtected = isProtected,
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
        createdAt: Long,
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
                snapshotDate = createdAt.toUtcDate(),
                isAdjustment = isAdjustment,
                createdAtEpochMillis = createdAt,
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

    private fun Long.toUtcDate(): String {
        return java.time.Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().toString()
    }

    private companion object {
        const val TEST_DATABASE_NAME = "money-tracker-stats-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_stats_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_stats_test_key"

        const val JUNE_01 = 1_780_272_000_000L
        const val JUNE_30 = 1_782_777_600_000L
        const val JULY_01 = 1_782_864_000_000L
        const val JULY_02 = 1_782_950_400_000L
        const val JULY_03 = 1_783_036_800_000L
        const val JULY_04 = 1_783_123_200_000L
        const val JULY_10 = 1_783_641_600_000L
        const val JULY_13 = 1_783_900_800_000L
        const val JULY_15 = 1_784_073_600_000L
        const val JULY_16 = 1_784_160_000_000L
        const val JULY_20 = 1_784_505_600_000L
        const val AUGUST_01 = 1_785_542_400_000L
    }
}
