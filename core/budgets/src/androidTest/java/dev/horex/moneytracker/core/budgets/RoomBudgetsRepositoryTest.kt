package dev.horex.moneytracker.core.budgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.BudgetEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateSnapshotEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RoomBudgetsRepositoryTest {
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
    fun listBudgetsCalculatesMonthlyProgressAndCrossCurrencySnapshotAmounts() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val usdAccountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val eurAccountId = insertAccount(database, profileId, name = "Euro", currency = "EUR")
            val foodCategoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val travelCategoryId = insertCategory(database, profileId, name = "Travel", type = "expense")
            database.exchangeRateSnapshotDao().upsert(
                ExchangeRateSnapshotEntity(
                    snapshotDate = JULY_03.toUtcDate(),
                    baseCurrency = "EUR",
                    targetCurrency = "USD",
                    rateE8 = 200_000_000L,
                    createdAtEpochMillis = JULY_01,
                ),
            )
            database.budgetDao().insert(
                BudgetEntity(
                    profileId = profileId,
                    categoryId = foodCategoryId,
                    limitCents = 10_000,
                    period = "monthly",
                    currencyCode = "USD",
                    lastNotifiedPercent = 0,
                    createdAtEpochMillis = JULY_01,
                    updatedAtEpochMillis = JULY_01,
                ),
            )
            insertTransaction(database, profileId, usdAccountId, foodCategoryId, "expense", 2_500, "USD", JULY_02)
            insertTransaction(database, profileId, eurAccountId, foodCategoryId, "expense", 1_500, "EUR", JULY_03)
            insertTransaction(database, profileId, usdAccountId, foodCategoryId, "income", 9_000, "USD", JULY_04)
            insertTransaction(database, profileId, usdAccountId, travelCategoryId, "expense", 9_000, "USD", JULY_04)
            insertTransaction(database, profileId, usdAccountId, foodCategoryId, "expense", 8_000, "USD", JUNE_30)
            insertTransaction(database, profileId, usdAccountId, foodCategoryId, "expense", 7_000, "USD", AUGUST_01)
            val repository = RoomBudgetsRepository(database, clock = { JULY_15 }, zoneId = ZoneOffset.UTC)

            val budget = repository.listBudgets(profileId).single()

            assertEquals(5_500L, budget.spentCents)
            assertEquals(55.0, budget.usagePercent, 0.001)
            assertFalse(budget.isOverLimit)
            assertEquals("Food", budget.categoryName)
            assertEquals(listOf(50), budget.crossedAlertThresholds(JULY_01))
        } finally {
            database.close()
        }
    }

    @Test
    fun weeklyPeriodUsesMondayInclusiveAndNextMondayExclusiveBounds() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val categoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val budgetId = database.budgetDao().insert(
                BudgetEntity(
                    profileId = profileId,
                    categoryId = categoryId,
                    limitCents = 10_000,
                    period = "weekly",
                    currencyCode = "USD",
                    createdAtEpochMillis = JULY_01,
                    updatedAtEpochMillis = JULY_01,
                ),
            )
            insertTransaction(database, profileId, accountId, categoryId, "expense", 900, "USD", JULY_12)
            insertTransaction(database, profileId, accountId, categoryId, "expense", 100, "USD", JULY_13)
            insertTransaction(database, profileId, accountId, categoryId, "expense", 200, "USD", JULY_15)
            insertTransaction(database, profileId, accountId, categoryId, "expense", 300, "USD", JULY_19)
            insertTransaction(database, profileId, accountId, categoryId, "expense", 400, "USD", JULY_20)
            val repository = RoomBudgetsRepository(database, clock = { JULY_15 }, zoneId = ZoneOffset.UTC)

            val budget = repository.getBudget(profileId, budgetId)

            assertEquals(600L, budget.spentCents)
            assertEquals(BudgetPeriod.Weekly, budget.period)
        } finally {
            database.close()
        }
    }

    @Test
    fun listBudgetTransactionsReturnsOnlyExpenseTransactionsForBudgetPeriod() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val cashAccountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val cardAccountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val otherAccountId = insertAccount(database, otherProfileId, name = "Other", currency = "USD")
            val foodCategoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val travelCategoryId = insertCategory(database, profileId, name = "Travel", type = "expense")
            val otherCategoryId = insertCategory(database, otherProfileId, name = "Other food", type = "expense")
            val repository = RoomBudgetsRepository(database, clock = { JULY_15 }, zoneId = ZoneOffset.UTC)
            val budget = repository.createBudget(
                profileId,
                CreateBudgetInput(
                    categoryId = foodCategoryId,
                    limitCents = 10_000,
                    period = BudgetPeriod.Monthly,
                    currencyCode = "usd",
                ),
            )
            val lunchId = insertTransaction(database, profileId, cashAccountId, foodCategoryId, "expense", 100, "USD", JULY_02, note = "Lunch")
            val dinnerId = insertTransaction(database, profileId, cardAccountId, foodCategoryId, "expense", 300, "USD", JULY_03, note = "Dinner")
            insertTransaction(database, profileId, cashAccountId, foodCategoryId, "income", 9_000, "USD", JULY_04)
            insertTransaction(database, profileId, cashAccountId, foodCategoryId, "expense", 500, "USD", JULY_04, isAdjustment = true)
            insertTransaction(database, profileId, cashAccountId, travelCategoryId, "expense", 600, "USD", JULY_05)
            insertTransaction(database, profileId, cashAccountId, foodCategoryId, "expense", 700, "USD", AUGUST_01)
            insertTransaction(database, otherProfileId, otherAccountId, otherCategoryId, "expense", 800, "USD", JULY_03)

            val transactions = repository.listBudgetTransactions(profileId, budget.id)

            assertEquals(listOf(dinnerId, lunchId), transactions.map { it.id })
            assertEquals(listOf("Dinner", "Lunch"), transactions.map { it.note })
            assertEquals(listOf("Card", "Cash"), transactions.map { it.accountName })
            assertTrue(transactions.all { it.categoryName == "Food" })
        } finally {
            database.close()
        }
    }

    @Test
    fun recordBudgetThresholdNotificationPersistsStateAndResetsForNewPeriod() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val categoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val budgetId = database.budgetDao().insert(
                BudgetEntity(
                    profileId = profileId,
                    categoryId = categoryId,
                    limitCents = 10_000,
                    period = "monthly",
                    currencyCode = "USD",
                    lastNotifiedPercent = 0,
                    lastNotifiedAtEpochMillis = null,
                    createdAtEpochMillis = JULY_01,
                    updatedAtEpochMillis = JULY_01,
                ),
            )
            val repository = RoomBudgetsRepository(database, clock = { JULY_15 }, zoneId = ZoneOffset.UTC)

            assertTrue(
                repository.recordBudgetThresholdNotification(
                    profileId = profileId,
                    budgetId = budgetId,
                    thresholdPercent = 75,
                    periodStartEpochMillis = JULY_01,
                    notifiedAtEpochMillis = JULY_15,
                ),
            )
            assertFalse(
                repository.recordBudgetThresholdNotification(
                    profileId = profileId,
                    budgetId = budgetId,
                    thresholdPercent = 75,
                    periodStartEpochMillis = JULY_01,
                    notifiedAtEpochMillis = JULY_15,
                ),
            )
            assertFalse(
                repository.recordBudgetThresholdNotification(
                    profileId = profileId,
                    budgetId = budgetId,
                    thresholdPercent = 50,
                    periodStartEpochMillis = JULY_01,
                    notifiedAtEpochMillis = JULY_15,
                ),
            )
            assertTrue(
                repository.recordBudgetThresholdNotification(
                    profileId = profileId,
                    budgetId = budgetId,
                    thresholdPercent = 95,
                    periodStartEpochMillis = JULY_01,
                    notifiedAtEpochMillis = JULY_15,
                ),
            )

            val currentPeriodBudget = repository.getBudget(profileId, budgetId)
            assertEquals(95, currentPeriodBudget.lastNotifiedPercent)
            assertEquals(JULY_15, currentPeriodBudget.lastNotifiedAtEpochMillis)

            assertTrue(
                repository.recordBudgetThresholdNotification(
                    profileId = profileId,
                    budgetId = budgetId,
                    thresholdPercent = 50,
                    periodStartEpochMillis = AUGUST_01,
                    notifiedAtEpochMillis = AUGUST_01,
                ),
            )

            val nextPeriodBudget = repository.getBudget(profileId, budgetId)
            assertEquals(50, nextPeriodBudget.lastNotifiedPercent)
            assertEquals(AUGUST_01, nextPeriodBudget.lastNotifiedAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun createUpdateDeleteValidateInputDuplicatesCategoryAndProfileScope() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val expenseCategoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val incomeCategoryId = insertCategory(database, profileId, name = "Salary", type = "income")
            val otherCategoryId = insertCategory(database, otherProfileId, name = "Food", type = "expense")
            val repository = RoomBudgetsRepository(database, clock = { JULY_15 }, zoneId = ZoneOffset.UTC)

            val monthly = repository.createBudget(
                profileId,
                CreateBudgetInput(expenseCategoryId, 10_000, BudgetPeriod.Monthly, "usd"),
            )
            val weekly = repository.createBudget(
                profileId,
                CreateBudgetInput(expenseCategoryId, 2_500, BudgetPeriod.Weekly, "USD"),
            )
            repository.createBudget(
                otherProfileId,
                CreateBudgetInput(otherCategoryId, 3_000, BudgetPeriod.Monthly, "USD"),
            )
            val updated = repository.updateBudget(
                profileId,
                monthly.id,
                UpdateBudgetInput(limitCents = 12_500, notificationsEnabled = false),
            )

            assertEquals(12_500L, updated.limitCents)
            assertFalse(updated.notificationsEnabled)
            assertEquals(BudgetPeriod.Monthly, updated.period)
            assertEquals("USD", updated.currencyCode)

            assertFailsWithType<InvalidBudgetAmountException> {
                repository.createBudget(profileId, CreateBudgetInput(expenseCategoryId, 0, BudgetPeriod.Monthly, "USD"))
            }
            assertFailsWithType<InvalidBudgetCurrencyException> {
                repository.createBudget(profileId, CreateBudgetInput(expenseCategoryId, 100, BudgetPeriod.Monthly, "US"))
            }
            assertFailsWithType<BudgetCategoryTypeException> {
                repository.createBudget(profileId, CreateBudgetInput(incomeCategoryId, 100, BudgetPeriod.Monthly, "USD"))
            }
            assertFailsWithType<BudgetAlreadyExistsException> {
                repository.createBudget(profileId, CreateBudgetInput(expenseCategoryId, 100, BudgetPeriod.Monthly, "USD"))
            }
            assertFailsWithType<BudgetAlreadyExistsException> {
                repository.updateBudget(profileId, weekly.id, UpdateBudgetInput(period = BudgetPeriod.Monthly))
            }
            assertFailsWithType<BudgetNotFoundException> {
                repository.getBudget(otherProfileId, monthly.id)
            }

            repository.deleteBudget(profileId, monthly.id)
            assertFailsWithType<BudgetNotFoundException> {
                repository.getBudget(profileId, monthly.id)
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
    ): Long {
        return database.categoryDao().insert(
            CategoryEntity(
                profileId = profileId,
                name = name,
                icon = "tag",
                type = type,
                color = "#64748b",
                isProtected = type == "transfer" || type == "adjustment",
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
        note: String = "",
        isAdjustment: Boolean = false,
    ): Long {
        return database.transactionDao().insert(
            TransactionEntity(
                profileId = profileId,
                type = type,
                amountCents = amountCents,
                categoryId = categoryId,
                accountId = accountId,
                note = note,
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
        const val TEST_DATABASE_NAME = "money-tracker-budgets-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_budgets_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_budgets_test_key"

        val JUNE_30 = utcDate(2026, 6, 30)
        val JULY_01 = utcDate(2026, 7, 1)
        val JULY_02 = utcDate(2026, 7, 2)
        val JULY_03 = utcDate(2026, 7, 3)
        val JULY_04 = utcDate(2026, 7, 4)
        val JULY_05 = utcDate(2026, 7, 5)
        val JULY_12 = utcDate(2026, 7, 12)
        val JULY_13 = utcDate(2026, 7, 13)
        val JULY_15 = utcDate(2026, 7, 15)
        val JULY_19 = utcDate(2026, 7, 19)
        val JULY_20 = utcDate(2026, 7, 20)
        val AUGUST_01 = utcDate(2026, 8, 1)

        fun utcDate(year: Int, month: Int, dayOfMonth: Int): Long {
            return LocalDate.of(year, month, dayOfMonth)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
    }
}
