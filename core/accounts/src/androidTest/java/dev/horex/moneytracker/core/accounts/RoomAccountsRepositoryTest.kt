package dev.horex.moneytracker.core.accounts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.model.TransactionTemplateEntity
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
class RoomAccountsRepositoryTest {
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
    fun createAccountMakesFirstAccountDefaultAndSubsequentAccountsNonDefault() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomAccountsRepository(database, clock = { 10L })

            val first = repository.createAccount(
                profileId,
                CreateAccountInput(name = "Main", currencyCode = "usd"),
            )
            val second = repository.createAccount(
                profileId,
                CreateAccountInput(name = "Cash", type = AccountType.Cash, currencyCode = "EUR"),
            )

            assertTrue(first.isDefault)
            assertFalse(second.isDefault)
            assertEquals("USD", first.currencyCode)
            assertEquals(AccountType.Cash, second.type)
            assertEquals(2, repository.listAccounts(profileId).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun updateAccountChangesMutableFieldsButRejectsCurrencyChange() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomAccountsRepository(database, clock = { 20L })
            val account = repository.createAccount(
                profileId,
                CreateAccountInput(name = "Main", currencyCode = "USD"),
            )

            val updated = repository.updateAccount(
                profileId = profileId,
                accountId = account.id,
                input = UpdateAccountInput(
                    name = "Everyday",
                    icon = "credit-card",
                    color = "#10b981",
                    type = AccountType.Credit,
                    includeInTotal = false,
                ),
            )

            assertEquals("Everyday", updated.name)
            assertEquals("credit-card", updated.icon)
            assertEquals("#10b981", updated.color)
            assertEquals(AccountType.Credit, updated.type)
            assertFalse(updated.includeInTotal)
            assertEquals(20L, updated.updatedAtEpochMillis)

            assertFailsWithType<CurrencyImmutableException> {
                repository.updateAccount(
                    profileId,
                    account.id,
                    UpdateAccountInput(currencyCode = "EUR"),
                )
            }
            assertFailsWithType<InvalidAccountCurrencyException> {
                repository.updateAccount(
                    profileId,
                    account.id,
                    UpdateAccountInput(currencyCode = "EU"),
                )
            }
            assertFailsWithType<InvalidAccountCurrencyException> {
                repository.createAccount(
                    profileId,
                    CreateAccountInput(name = "Unsupported", currencyCode = "XXX"),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun setDefaultAccountMovesDefaultFlagWithinProfile() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val repository = RoomAccountsRepository(database, clock = { 30L })
            val first = repository.createAccount(profileId, CreateAccountInput("Main", currencyCode = "USD"))
            val second = repository.createAccount(profileId, CreateAccountInput("Savings", currencyCode = "USD"))
            val other = repository.createAccount(otherProfileId, CreateAccountInput("Other", currencyCode = "USD"))

            val promoted = repository.setDefaultAccount(profileId, second.id)

            assertEquals(second.id, promoted.id)
            assertTrue(promoted.isDefault)
            assertFalse(repository.getAccount(profileId, first.id).isDefault)
            assertTrue(repository.getAccount(otherProfileId, other.id).isDefault)
            assertFailsWithType<AccountNotFoundException> {
                repository.setDefaultAccount(profileId, other.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteAccountRejectsLastAccount() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomAccountsRepository(database)
            val account = repository.createAccount(profileId, CreateAccountInput("Main", currencyCode = "USD"))

            assertFailsWithType<CannotDeleteLastAccountException> {
                repository.deleteAccount(profileId, account.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteDefaultAccountWithTwoAccountsPromotesRemainingAccount() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomAccountsRepository(database, clock = { 40L })
            val first = repository.createAccount(profileId, CreateAccountInput("Main", currencyCode = "USD"))
            val second = repository.createAccount(profileId, CreateAccountInput("Savings", currencyCode = "USD"))

            repository.deleteAccount(profileId, first.id)

            val remaining = repository.getAccount(profileId, second.id)
            assertTrue(remaining.isDefault)
            assertEquals(listOf(second.id), repository.listAccounts(profileId).map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteDefaultAccountWithMoreThanTwoAccountsRequiresNewDefault() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomAccountsRepository(database)
            val first = repository.createAccount(profileId, CreateAccountInput("Main", currencyCode = "USD"))
            repository.createAccount(profileId, CreateAccountInput("Savings", currencyCode = "USD"))
            repository.createAccount(profileId, CreateAccountInput("Cash", currencyCode = "USD"))

            assertFailsWithType<MustSetNewDefaultAccountException> {
                repository.deleteAccount(profileId, first.id)
            }
            assertTrue(repository.getAccount(profileId, first.id).isDefault)
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteAccountRejectsLinkedRowsBeforeForeignKeyFailure() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomAccountsRepository(database)

            val txAccount = createDeletableAccount(repository, profileId, "Transactions")
            val transferAccount = createDeletableAccount(repository, profileId, "Transfers")
            val recurringAccount = createDeletableAccount(repository, profileId, "Recurring")
            val templateAccount = createDeletableAccount(repository, profileId, "Templates")
            val peerAccount = createDeletableAccount(repository, profileId, "Peer")
            val categoryId = insertCategory(database, profileId)

            database.transactionDao().insert(
                TransactionEntity(
                    profileId = profileId,
                    type = "expense",
                    amountCents = 100,
                    categoryId = categoryId,
                    accountId = txAccount.id,
                    currencyCode = "USD",
                    snapshotDate = "2026-07-01",
                    createdAtEpochMillis = 1L,
                ),
            )
            database.transferDao().insert(
                TransferEntity(
                    profileId = profileId,
                    fromAccountId = transferAccount.id,
                    toAccountId = peerAccount.id,
                    amountCents = 100,
                    fromCurrencyCode = "USD",
                    toCurrencyCode = "USD",
                    createdAtEpochMillis = 1L,
                ),
            )
            database.recurringTransactionDao().insert(
                RecurringTransactionEntity(
                    profileId = profileId,
                    accountId = recurringAccount.id,
                    categoryId = categoryId,
                    type = "expense",
                    amountCents = 100,
                    currencyCode = "USD",
                    frequency = "monthly",
                    nextRunAtEpochMillis = 1L,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            )
            database.transactionTemplateDao().insert(
                TransactionTemplateEntity(
                    profileId = profileId,
                    accountId = templateAccount.id,
                    categoryId = categoryId,
                    type = "expense",
                    amountCents = 100,
                    currencyCode = "USD",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            )

            assertFailsWithType<AccountHasTransactionsException> {
                repository.deleteAccount(profileId, txAccount.id)
            }
            assertFailsWithType<AccountHasTransfersException> {
                repository.deleteAccount(profileId, transferAccount.id)
            }
            assertFailsWithType<AccountHasRecurringTransactionsException> {
                repository.deleteAccount(profileId, recurringAccount.id)
            }
            assertFailsWithType<AccountHasTemplatesException> {
                repository.deleteAccount(profileId, templateAccount.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun accountBalanceIsDerivedFromTransactions() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomAccountsRepository(database)
            val account = repository.createAccount(profileId, CreateAccountInput("Main", currencyCode = "USD"))
            val categoryId = insertCategory(database, profileId)

            database.transactionDao().insert(
                TransactionEntity(
                    profileId = profileId,
                    type = "income",
                    amountCents = 10_000,
                    categoryId = categoryId,
                    accountId = account.id,
                    currencyCode = "USD",
                    snapshotDate = "2026-07-01",
                    createdAtEpochMillis = 1L,
                ),
            )
            database.transactionDao().insert(
                TransactionEntity(
                    profileId = profileId,
                    type = "expense",
                    amountCents = 2_500,
                    categoryId = categoryId,
                    accountId = account.id,
                    currencyCode = "USD",
                    snapshotDate = "2026-07-01",
                    createdAtEpochMillis = 2L,
                ),
            )

            assertEquals(7_500, repository.getAccount(profileId, account.id).balanceCents)
            assertEquals(7_500, repository.listAccounts(profileId).single().balanceCents)
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

    private suspend fun createDeletableAccount(
        repository: RoomAccountsRepository,
        profileId: Long,
        name: String,
    ): Account {
        return repository.createAccount(profileId, CreateAccountInput(name = name, currencyCode = "USD"))
    }

    private suspend fun insertCategory(database: MoneyTrackerDatabase, profileId: Long): Long {
        return database.categoryDao().insert(
            CategoryEntity(
                profileId = profileId,
                name = "General-${System.nanoTime()}",
                type = "both",
                color = "#64748b",
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
        const val TEST_DATABASE_NAME = "money-tracker-accounts-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_accounts_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_accounts_test_key"
    }
}
