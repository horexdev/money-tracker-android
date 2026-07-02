package dev.horex.moneytracker.core.transactions

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTransactionsRepositoryTest {
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
    fun addTransactionDerivesCurrencyFromAccountAndSnapshotDateFromCreatedAt() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId, name = "Card", currency = "EUR")
            val categoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val repository = RoomTransactionsRepository(database, clock = { 1_788_220_800_000L })

            val transaction = repository.addTransaction(
                profileId,
                CreateTransactionInput(
                    type = TransactionType.Expense,
                    amountCents = 1_250,
                    categoryId = categoryId,
                    accountId = accountId,
                    note = "Lunch",
                    createdAtEpochMillis = 1_788_264_000_000L,
                ),
            )

            assertEquals(TransactionType.Expense, transaction.type)
            assertEquals(1_250L, transaction.amountCents)
            assertEquals("EUR", transaction.currencyCode)
            assertEquals("2026-09-01", transaction.snapshotDate)
            assertEquals(1_788_264_000_000L, transaction.createdAtEpochMillis)
            assertEquals("Food", transaction.categoryName)
            assertEquals("Card", transaction.accountName)
            assertFalse(transaction.isAdjustment)
        } finally {
            database.close()
        }
    }

    @Test
    fun addTransactionRejectsInvalidAmountMissingRelationsAndIncompatibleCategory() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId)
            val expenseCategoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val incomeCategoryId = insertCategory(database, profileId, name = "Salary", type = "income")
            val savingsCategoryId = insertCategory(database, profileId, name = "Savings", type = "savings")
            val deletedCategoryId = insertCategory(
                database = database,
                profileId = profileId,
                name = "Deleted",
                type = "expense",
                deletedAt = 5L,
            )
            val repository = RoomTransactionsRepository(database)

            assertFailsWithType<InvalidTransactionAmountException> {
                repository.addTransaction(
                    profileId,
                    CreateTransactionInput(TransactionType.Expense, 0, expenseCategoryId, accountId),
                )
            }
            assertFailsWithType<TransactionAccountNotFoundException> {
                repository.addTransaction(
                    profileId,
                    CreateTransactionInput(TransactionType.Expense, 100, expenseCategoryId, 999),
                )
            }
            assertFailsWithType<TransactionCategoryNotFoundException> {
                repository.addTransaction(
                    profileId,
                    CreateTransactionInput(TransactionType.Expense, 100, deletedCategoryId, accountId),
                )
            }
            assertFailsWithType<TransactionCategoryTypeException> {
                repository.addTransaction(
                    profileId,
                    CreateTransactionInput(TransactionType.Expense, 100, incomeCategoryId, accountId),
                )
            }
            assertFailsWithType<TransactionCategoryTypeException> {
                repository.addTransaction(
                    profileId,
                    CreateTransactionInput(TransactionType.Income, 100, savingsCategoryId, accountId),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun listTransactionsFiltersPagesAndExcludesAdjustments() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val firstAccountId = insertAccount(database, profileId, name = "Cash")
            val secondAccountId = insertAccount(database, profileId, name = "Card")
            val otherAccountId = insertAccount(database, otherProfileId, name = "Other")
            val foodCategoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val salaryCategoryId = insertCategory(database, profileId, name = "Salary", type = "income")
            val otherCategoryId = insertCategory(database, otherProfileId, name = "Other category", type = "expense")
            val repository = RoomTransactionsRepository(database)

            val oldFood = repository.addTransaction(
                profileId,
                CreateTransactionInput(TransactionType.Expense, 100, foodCategoryId, firstAccountId, createdAtEpochMillis = 10),
            )
            val newFood = repository.addTransaction(
                profileId,
                CreateTransactionInput(TransactionType.Expense, 200, foodCategoryId, secondAccountId, createdAtEpochMillis = 20),
            )
            repository.addTransaction(
                profileId,
                CreateTransactionInput(TransactionType.Income, 300, salaryCategoryId, firstAccountId, createdAtEpochMillis = 30),
            )
            insertTransaction(
                database = database,
                profileId = profileId,
                accountId = firstAccountId,
                categoryId = foodCategoryId,
                isAdjustment = true,
                createdAt = 40,
            )
            repository.addTransaction(
                otherProfileId,
                CreateTransactionInput(TransactionType.Expense, 900, otherCategoryId, otherAccountId, createdAtEpochMillis = 50),
            )

            val firstPage = repository.listTransactions(profileId, TransactionQuery(page = 1, pageSize = 2))
            assertEquals(2, firstPage.totalPages)
            assertEquals(1, firstPage.currentPage)
            assertEquals(listOf(300L, 200L), firstPage.transactions.map { it.amountCents })

            val lastPage = repository.listTransactions(profileId, TransactionQuery(page = 99, pageSize = 2))
            assertEquals(2, lastPage.currentPage)
            assertEquals(listOf(oldFood.id), lastPage.transactions.map { it.id })

            val filtered = repository.listTransactions(
                profileId,
                TransactionQuery(
                    accountId = secondAccountId,
                    categoryId = foodCategoryId,
                    fromEpochMillis = 15,
                    toEpochMillis = 25,
                    pageSize = 200,
                ),
            )
            assertEquals(1, filtered.totalPages)
            assertEquals(listOf(newFood.id), filtered.transactions.map { it.id })
            assertTrue(filtered.transactions.none { it.isAdjustment })
        } finally {
            database.close()
        }
    }

    @Test
    fun updateTransactionChangesEditableFieldsButPreservesSnapshotDate() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId, currency = "USD")
            val foodCategoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val travelCategoryId = insertCategory(database, profileId, name = "Travel", type = "expense")
            val incomeCategoryId = insertCategory(database, profileId, name = "Salary", type = "income")
            val repository = RoomTransactionsRepository(database)
            val transaction = repository.addTransaction(
                profileId,
                CreateTransactionInput(
                    type = TransactionType.Expense,
                    amountCents = 100,
                    categoryId = foodCategoryId,
                    accountId = accountId,
                    note = "Old",
                    createdAtEpochMillis = 1_788_264_000_000L,
                ),
            )

            val updated = repository.updateTransaction(
                profileId = profileId,
                transactionId = transaction.id,
                input = UpdateTransactionInput(
                    amountCents = 250,
                    categoryId = travelCategoryId,
                    note = "New",
                    createdAtEpochMillis = 1_788_350_400_000L,
                ),
            )

            assertEquals(250L, updated.amountCents)
            assertEquals(travelCategoryId, updated.categoryId)
            assertEquals("Travel", updated.categoryName)
            assertEquals("New", updated.note)
            assertEquals(1_788_350_400_000L, updated.createdAtEpochMillis)
            assertEquals("2026-09-01", updated.snapshotDate)
            assertEquals("USD", updated.currencyCode)

            assertFailsWithType<TransactionCategoryTypeException> {
                repository.updateTransaction(
                    profileId,
                    transaction.id,
                    UpdateTransactionInput(300, incomeCategoryId),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun updateAndDeleteRejectAdjustmentAndLinkedTransferTransactions() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val fromAccountId = insertAccount(database, profileId, name = "Cash")
            val toAccountId = insertAccount(database, profileId, name = "Card")
            val categoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val repository = RoomTransactionsRepository(database)
            val normal = repository.addTransaction(
                profileId,
                CreateTransactionInput(TransactionType.Expense, 100, categoryId, fromAccountId),
            )
            val linked = repository.addTransaction(
                profileId,
                CreateTransactionInput(TransactionType.Expense, 200, categoryId, fromAccountId),
            )
            val adjustmentId = insertTransaction(
                database = database,
                profileId = profileId,
                accountId = fromAccountId,
                categoryId = categoryId,
                isAdjustment = true,
                createdAt = 30,
            )
            database.transferDao().insert(
                TransferEntity(
                    profileId = profileId,
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amountCents = 200,
                    fromCurrencyCode = "USD",
                    toCurrencyCode = "USD",
                    fromTransactionId = linked.id,
                    createdAtEpochMillis = 40,
                ),
            )

            assertFailsWithType<AdjustmentTransactionImmutableException> {
                repository.updateTransaction(
                    profileId,
                    adjustmentId,
                    UpdateTransactionInput(150, categoryId),
                )
            }
            assertFailsWithType<AdjustmentTransactionImmutableException> {
                repository.deleteTransaction(profileId, adjustmentId)
            }
            assertFailsWithType<TransactionLinkedToTransferException> {
                repository.updateTransaction(
                    profileId,
                    linked.id,
                    UpdateTransactionInput(250, categoryId),
                )
            }
            assertFailsWithType<TransactionLinkedToTransferException> {
                repository.deleteTransaction(profileId, linked.id)
            }

            repository.deleteTransaction(profileId, normal.id)
            assertFailsWithType<TransactionNotFoundException> {
                repository.getTransaction(profileId, normal.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun transactionOperationsAreScopedToProfile() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val accountId = insertAccount(database, otherProfileId, name = "Other")
            val categoryId = insertCategory(database, otherProfileId, name = "Other category", type = "expense")
            val repository = RoomTransactionsRepository(database)
            val otherTransaction = repository.addTransaction(
                otherProfileId,
                CreateTransactionInput(TransactionType.Expense, 100, categoryId, accountId),
            )

            assertEquals(emptyList<MoneyTransaction>(), repository.listTransactions(profileId).transactions)
            assertFailsWithType<TransactionNotFoundException> {
                repository.getTransaction(profileId, otherTransaction.id)
            }
            assertFailsWithType<TransactionNotFoundException> {
                repository.deleteTransaction(profileId, otherTransaction.id)
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
        name: String = "Main",
        currency: String = "USD",
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
        deletedAt: Long? = null,
    ): Long {
        return database.categoryDao().insert(
            CategoryEntity(
                profileId = profileId,
                name = name,
                type = type,
                color = "#64748b",
                isProtected = type == "transfer" || type == "adjustment",
                updatedAtEpochMillis = 1L,
                deletedAtEpochMillis = deletedAt,
            ),
        )
    }

    private suspend fun insertTransaction(
        database: MoneyTrackerDatabase,
        profileId: Long,
        accountId: Long,
        categoryId: Long,
        isAdjustment: Boolean,
        createdAt: Long,
    ): Long {
        return database.transactionDao().insert(
            TransactionEntity(
                profileId = profileId,
                type = "expense",
                amountCents = 100,
                categoryId = categoryId,
                accountId = accountId,
                note = "",
                currencyCode = "USD",
                snapshotDate = "2026-07-01",
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

    private companion object {
        const val TEST_DATABASE_NAME = "money-tracker-transactions-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_transactions_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_transactions_test_key"
    }
}
