package dev.horex.moneytracker.core.transfers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.currency.RATE_SCALE_E8
import dev.horex.moneytracker.core.currency.RoomCurrencyRatesRepository
import dev.horex.moneytracker.core.currency.SaveExchangeRateSnapshotInput
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTransfersRepositoryTest {
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
    fun createTransferCreatesLinkedTransactionsWithExplicitCrossCurrencyRate() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val fromAccountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val toAccountId = insertAccount(database, profileId, name = "Card", currency = "EUR")
            val transferCategoryId = insertCategory(database, profileId, name = "Transfer", type = "transfer")
            val repository = RoomTransfersRepository(database)

            val transfer = repository.createTransfer(
                profileId,
                CreateTransferInput(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amountCents = 1_000,
                    exchangeRateE8 = 92_000_000,
                    note = "Move cash",
                    createdAtEpochMillis = 1_788_264_000_000L,
                ),
            )

            assertEquals(fromAccountId, transfer.fromAccountId)
            assertEquals("Cash", transfer.fromAccountName)
            assertEquals(toAccountId, transfer.toAccountId)
            assertEquals("Card", transfer.toAccountName)
            assertEquals(1_000L, transfer.amountCents)
            assertEquals("USD", transfer.fromCurrencyCode)
            assertEquals("EUR", transfer.toCurrencyCode)
            assertEquals(92_000_000L, transfer.exchangeRateE8)
            assertEquals("Move cash", transfer.note)
            assertEquals(1_788_264_000_000L, transfer.createdAtEpochMillis)
            assertNotNull(transfer.fromTransactionId)
            assertNotNull(transfer.toTransactionId)

            val fromTransaction = checkNotNull(database.transactionDao().getById(profileId, transfer.fromTransactionId!!))
            val toTransaction = checkNotNull(database.transactionDao().getById(profileId, transfer.toTransactionId!!))
            assertEquals("expense", fromTransaction.type)
            assertEquals(1_000L, fromTransaction.amountCents)
            assertEquals("USD", fromTransaction.currencyCode)
            assertEquals(fromAccountId, fromTransaction.accountId)
            assertEquals(transferCategoryId, fromTransaction.categoryId)
            assertEquals("2026-09-01", fromTransaction.snapshotDate)
            assertEquals("income", toTransaction.type)
            assertEquals(920L, toTransaction.amountCents)
            assertEquals("EUR", toTransaction.currencyCode)
            assertEquals(toAccountId, toTransaction.accountId)
            assertEquals(transferCategoryId, toTransaction.categoryId)

            assertEquals(-1_000L, database.accountDao().getBalanceCents(profileId, fromAccountId))
            assertEquals(920L, database.accountDao().getBalanceCents(profileId, toAccountId))
            assertEquals(1, database.transactionDao().countTransferLinks(profileId, fromTransaction.id))
            assertEquals(1, database.transactionDao().countTransferLinks(profileId, toTransaction.id))
            assertTrue(database.categoryDao().listByFrequency(profileId).none { it.id == transferCategoryId })
        } finally {
            database.close()
        }
    }

    @Test
    fun createTransferResolvesMissingCrossCurrencyRateFromCurrencyRepository() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val fromAccountId = insertAccount(database, profileId, name = "USD", currency = "USD")
            val toAccountId = insertAccount(database, profileId, name = "EUR", currency = "EUR")
            insertCategory(database, profileId, name = "Transfer", type = "transfer")
            RoomCurrencyRatesRepository(database).saveSnapshot(
                SaveExchangeRateSnapshotInput(
                    snapshotDate = "2026-09-01",
                    baseCurrency = "USD",
                    targetCurrency = "EUR",
                    rateE8 = 90_000_000,
                ),
            )
            val repository = RoomTransfersRepository(database)

            val transfer = repository.createTransfer(
                profileId,
                CreateTransferInput(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amountCents = 1_000,
                    createdAtEpochMillis = 1_788_264_000_000L,
                ),
            )

            val toTransaction = checkNotNull(database.transactionDao().getById(profileId, transfer.toTransactionId!!))
            assertEquals(90_000_000L, transfer.exchangeRateE8)
            assertEquals(900L, toTransaction.amountCents)

            val fallbackTransfer = repository.createTransfer(
                profileId,
                CreateTransferInput(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amountCents = 100,
                    exchangeRateE8 = -5,
                    createdAtEpochMillis = 1_788_264_000_000L,
                ),
            )
            val fallbackToTransaction = checkNotNull(
                database.transactionDao().getById(profileId, fallbackTransfer.toTransactionId!!),
            )
            assertEquals(RATE_SCALE_E8, fallbackTransfer.exchangeRateE8)
            assertEquals(100L, fallbackToTransaction.amountCents)
        } finally {
            database.close()
        }
    }

    @Test
    fun createTransferRejectsInvalidInputAndMissingRelations() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val fromAccountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val toAccountId = insertAccount(database, profileId, name = "Card", currency = "EUR")
            val repository = RoomTransfersRepository(database)

            assertFailsWithType<TransferSameAccountException> {
                repository.createTransfer(
                    profileId,
                    CreateTransferInput(fromAccountId, fromAccountId, 100),
                )
            }
            assertFailsWithType<InvalidTransferAmountException> {
                repository.createTransfer(
                    profileId,
                    CreateTransferInput(fromAccountId, toAccountId, 0),
                )
            }
            assertFailsWithType<TransferAccountNotFoundException> {
                repository.createTransfer(
                    profileId,
                    CreateTransferInput(999, toAccountId, 100),
                )
            }
            assertFailsWithType<TransferCategoryNotFoundException> {
                repository.createTransfer(
                    profileId,
                    CreateTransferInput(fromAccountId, toAccountId, 100),
                )
            }

            insertCategory(database, profileId, name = "Transfer", type = "transfer")
            assertFailsWithType<TransferExchangeRateNotFoundException> {
                repository.createTransfer(
                    profileId,
                    CreateTransferInput(fromAccountId, toAccountId, 100),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun listAndGetTransfersArePagedFilteredAndScopedToProfile() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val fromAccountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val toAccountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val savingsAccountId = insertAccount(database, profileId, name = "Savings", currency = "USD")
            val otherFromId = insertAccount(database, otherProfileId, name = "Other cash", currency = "USD")
            val otherToId = insertAccount(database, otherProfileId, name = "Other card", currency = "USD")
            insertCategory(database, profileId, name = "Transfer", type = "transfer")
            insertCategory(database, otherProfileId, name = "Transfer", type = "transfer")
            val repository = RoomTransfersRepository(database)

            val oldTransfer = repository.createTransfer(
                profileId,
                CreateTransferInput(fromAccountId, toAccountId, 100, createdAtEpochMillis = 10),
            )
            val middleTransfer = repository.createTransfer(
                profileId,
                CreateTransferInput(fromAccountId, savingsAccountId, 200, createdAtEpochMillis = 20),
            )
            repository.createTransfer(
                profileId,
                CreateTransferInput(toAccountId, savingsAccountId, 300, createdAtEpochMillis = 30),
            )
            repository.createTransfer(
                otherProfileId,
                CreateTransferInput(otherFromId, otherToId, 900, createdAtEpochMillis = 40),
            )

            val firstPage = repository.listTransfers(profileId, TransferQuery(page = 1, pageSize = 2))
            assertEquals(2, firstPage.totalPages)
            assertEquals(1, firstPage.currentPage)
            assertEquals(listOf(300L, 200L), firstPage.transfers.map { it.amountCents })

            val lastPage = repository.listTransfers(profileId, TransferQuery(page = 99, pageSize = 2))
            assertEquals(2, lastPage.currentPage)
            assertEquals(listOf(oldTransfer.id), lastPage.transfers.map { it.id })

            val filtered = repository.listTransfers(profileId, TransferQuery(accountId = savingsAccountId))
            assertEquals(listOf(300L, 200L), filtered.transfers.map { it.amountCents })
            assertEquals(middleTransfer.id, repository.getTransfer(profileId, middleTransfer.id).id)
            assertFailsWithType<TransferNotFoundException> {
                repository.getTransfer(otherProfileId, middleTransfer.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteTransferRemovesLinkedTransactionsAndRestoresBalances() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val fromAccountId = insertAccount(database, profileId, name = "Cash", currency = "USD")
            val toAccountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            insertCategory(database, profileId, name = "Transfer", type = "transfer")
            val repository = RoomTransfersRepository(database)
            val transfer = repository.createTransfer(
                profileId,
                CreateTransferInput(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amountCents = 500,
                    note = "Undo",
                    createdAtEpochMillis = 50,
                ),
            )

            repository.deleteTransfer(profileId, transfer.id)

            assertFailsWithType<TransferNotFoundException> {
                repository.getTransfer(profileId, transfer.id)
            }
            assertEquals(null, database.transactionDao().getById(profileId, transfer.fromTransactionId!!))
            assertEquals(null, database.transactionDao().getById(profileId, transfer.toTransactionId!!))
            assertEquals(0L, database.accountDao().getBalanceCents(profileId, fromAccountId))
            assertEquals(0L, database.accountDao().getBalanceCents(profileId, toAccountId))
            assertFailsWithType<TransferNotFoundException> {
                repository.deleteTransfer(profileId, transfer.id)
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
        const val TEST_DATABASE_NAME = "money-tracker-transfers-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_transfers_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_transfers_test_key"
    }
}
