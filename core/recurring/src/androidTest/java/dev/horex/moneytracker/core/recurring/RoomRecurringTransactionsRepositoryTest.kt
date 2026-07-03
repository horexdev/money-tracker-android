package dev.horex.moneytracker.core.recurring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import dev.horex.moneytracker.core.transactions.TransactionType
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRecurringTransactionsRepositoryTest {
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
    fun createUpdateToggleDeleteValidateInputAndProfileScope() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val accountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val otherAccountId = insertAccount(database, otherProfileId, name = "Other card", currency = "USD")
            val expenseCategoryId = insertCategory(database, profileId, name = "Rent", type = "expense")
            val incomeCategoryId = insertCategory(database, profileId, name = "Salary", type = "income")
            val otherCategoryId = insertCategory(database, otherProfileId, name = "Other rent", type = "expense")
            val repository = RoomRecurringTransactionsRepository(
                database = database,
                clock = { JULY_15 },
                zoneId = ZoneOffset.UTC,
            )

            val created = repository.createRecurring(
                profileId,
                CreateRecurringTransactionInput(
                    type = TransactionType.Expense,
                    amountCents = 120_000,
                    categoryId = expenseCategoryId,
                    accountId = accountId,
                    note = "Rent",
                    frequency = RecurringFrequency.Monthly,
                    nextRunAtEpochMillis = AUGUST_01,
                ),
            )

            assertEquals(profileId, created.profileId)
            assertEquals("USD", created.currencyCode)
            assertEquals("Rent", created.categoryName)
            assertTrue(created.isActive)
            assertEquals(listOf(created.id), repository.listRecurring(profileId).map { it.id })

            val updated = repository.updateRecurring(
                profileId = profileId,
                recurringId = created.id,
                input = UpdateRecurringTransactionInput(
                    amountCents = 125_000,
                    note = "",
                    frequency = RecurringFrequency.Weekly,
                    nextRunAtEpochMillis = JULY_20,
                ),
            )

            assertEquals(125_000L, updated.amountCents)
            assertEquals("", updated.note)
            assertEquals(RecurringFrequency.Weekly, updated.frequency)
            assertEquals(JULY_20, updated.nextRunAtEpochMillis)

            assertFalse(repository.toggleRecurringActive(profileId, created.id).isActive)
            assertTrue(repository.toggleRecurringActive(profileId, created.id).isActive)

            assertFailsWithType<RecurringTransactionNotFoundException> {
                repository.getRecurring(otherProfileId, created.id)
            }
            assertFailsWithType<RecurringAccountNotFoundException> {
                repository.createRecurring(
                    profileId,
                    CreateRecurringTransactionInput(
                        type = TransactionType.Expense,
                        amountCents = 100,
                        categoryId = expenseCategoryId,
                        accountId = otherAccountId,
                        frequency = RecurringFrequency.Monthly,
                    ),
                )
            }
            assertFailsWithType<RecurringCategoryNotFoundException> {
                repository.createRecurring(
                    profileId,
                    CreateRecurringTransactionInput(
                        type = TransactionType.Expense,
                        amountCents = 100,
                        categoryId = otherCategoryId,
                        accountId = accountId,
                        frequency = RecurringFrequency.Monthly,
                    ),
                )
            }
            assertFailsWithType<RecurringCategoryTypeException> {
                repository.createRecurring(
                    profileId,
                    CreateRecurringTransactionInput(
                        type = TransactionType.Expense,
                        amountCents = 100,
                        categoryId = incomeCategoryId,
                        accountId = accountId,
                        frequency = RecurringFrequency.Monthly,
                    ),
                )
            }
            assertFailsWithType<InvalidRecurringAmountException> {
                repository.updateRecurring(profileId, created.id, UpdateRecurringTransactionInput(amountCents = 0))
            }

            repository.deleteRecurring(profileId, created.id)
            assertFailsWithType<RecurringTransactionNotFoundException> {
                repository.getRecurring(profileId, created.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun processDueCreatesTransactionAdvancesNextRunAndDoesNotDuplicateCatchUp() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val categoryId = insertCategory(database, profileId, name = "Subscriptions", type = "expense")
            val repository = RoomRecurringTransactionsRepository(
                database = database,
                clock = { JULY_15 },
                zoneId = ZoneOffset.UTC,
            )
            val recurring = repository.createRecurring(
                profileId,
                CreateRecurringTransactionInput(
                    type = TransactionType.Expense,
                    amountCents = 999,
                    categoryId = categoryId,
                    accountId = accountId,
                    note = "Streaming",
                    frequency = RecurringFrequency.Monthly,
                    nextRunAtEpochMillis = JULY_01,
                ),
            )

            val first = repository.processDue(JULY_15)

            assertEquals(ProcessRecurringDueResult(processedCount = 1, skippedCount = 0), first)
            val transactions = database.transactionDao().listHistory(profileId, limit = 10, offset = 0)
            assertEquals(1, transactions.size)
            assertEquals(999L, transactions.single().amountCents)
            assertEquals("Streaming", transactions.single().note)
            assertEquals(JULY_15, transactions.single().createdAtEpochMillis)
            assertEquals("2026-07-15", transactions.single().snapshotDate)
            assertEquals(
                RecurringFrequency.Monthly.nextRunAfter(JULY_15, ZoneOffset.UTC),
                repository.getRecurring(profileId, recurring.id).nextRunAtEpochMillis,
            )
            assertEquals(1, database.recurringTransactionRunDao().countByRecurring(profileId, recurring.id))

            val second = repository.processDue(JULY_15)

            assertEquals(ProcessRecurringDueResult(processedCount = 0, skippedCount = 0), second)
            assertEquals(1, database.transactionDao().listHistory(profileId, limit = 10, offset = 0).size)

            val current = database.recurringTransactionDao().getById(profileId, recurring.id)
                ?: throw AssertionError("Expected recurring row")
            database.recurringTransactionDao().update(
                current.copy(
                    nextRunAtEpochMillis = JULY_01,
                    updatedAtEpochMillis = JULY_15,
                ),
            )

            val repeatedCatchUp = repository.processDue(JULY_15)

            assertEquals(ProcessRecurringDueResult(processedCount = 0, skippedCount = 1), repeatedCatchUp)
            assertEquals(1, database.transactionDao().listHistory(profileId, limit = 10, offset = 0).size)
            assertTrue(repository.getRecurring(profileId, recurring.id).nextRunAtEpochMillis > JULY_15)
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
        const val TEST_DATABASE_NAME = "money-tracker-recurring-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_recurring_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_recurring_test_key"

        val JULY_01 = utcDate(2026, 7, 1)
        val JULY_15 = utcDate(2026, 7, 15)
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
