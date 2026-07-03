package dev.horex.moneytracker.core.savings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationDeliveryResult
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSavingsGoalsRepositoryTest {
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
    fun createUpdateDeleteValidateInputAndProfileScope() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val accountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val otherAccountId = insertAccount(database, otherProfileId, name = "Other card", currency = "USD")
            insertSavingsCategory(database, profileId)
            val repository = RoomSavingsGoalsRepository(database = database, clock = { NOW })

            val created = repository.createGoal(
                profileId,
                CreateSavingsGoalInput(
                    name = "  Vacation  ",
                    targetCents = 100_000,
                    currencyCode = " usd ",
                    deadlineDate = "2026-12-31",
                    accountId = accountId,
                    createdAtEpochMillis = CREATED_AT,
                ),
            )

            assertEquals(profileId, created.profileId)
            assertEquals("Vacation", created.name)
            assertEquals(100_000L, created.targetCents)
            assertEquals(0L, created.currentCents)
            assertEquals("USD", created.currencyCode)
            assertEquals("2026-12-31", created.deadlineDate)
            assertEquals(accountId, created.accountId)
            assertEquals(CREATED_AT, created.createdAtEpochMillis)
            assertEquals(listOf(created.id), repository.listGoals(profileId).map { it.id })

            val updated = repository.updateGoal(
                profileId = profileId,
                goalId = created.id,
                input = UpdateSavingsGoalInput(
                    name = "Trip",
                    targetCents = 120_000,
                    clearDeadline = true,
                    clearLinkedAccount = true,
                ),
            )

            assertEquals("Trip", updated.name)
            assertEquals(120_000L, updated.targetCents)
            assertEquals(null, updated.deadlineDate)
            assertEquals(null, updated.accountId)
            assertEquals(NOW, updated.updatedAtEpochMillis)

            assertFailsWithType<SavingsGoalNotFoundException> {
                repository.getGoal(otherProfileId, created.id)
            }
            assertFailsWithType<SavingsGoalAccountNotFoundException> {
                repository.createGoal(
                    profileId,
                    CreateSavingsGoalInput(
                        name = "Wrong profile account",
                        targetCents = 1_000,
                        currencyCode = "USD",
                        accountId = otherAccountId,
                    ),
                )
            }
            assertFailsWithType<SavingsGoalNameEmptyException> {
                repository.createGoal(profileId, CreateSavingsGoalInput("   ", 1_000, "USD"))
            }
            assertFailsWithType<InvalidSavingsGoalAmountException> {
                repository.createGoal(profileId, CreateSavingsGoalInput("Bad target", 0, "USD"))
            }
            assertFailsWithType<InvalidSavingsGoalCurrencyException> {
                repository.createGoal(profileId, CreateSavingsGoalInput("Bad currency", 1_000, "US"))
            }
            assertFailsWithType<InvalidSavingsGoalDeadlineException> {
                repository.updateGoal(profileId, created.id, UpdateSavingsGoalInput(deadlineDate = "31-12-2026"))
            }
            assertFailsWithType<SavingsGoalLinkConflictException> {
                repository.updateGoal(
                    profileId,
                    created.id,
                    UpdateSavingsGoalInput(accountId = accountId, clearLinkedAccount = true),
                )
            }

            repository.deleteGoal(profileId, created.id)
            assertFailsWithType<SavingsGoalNotFoundException> {
                repository.getGoal(profileId, created.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun depositWithdrawHistoryAndCurrentStayConsistent() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId, name = "Savings card", currency = "USD")
            val savingsCategoryId = insertSavingsCategory(database, profileId)
            val repository = RoomSavingsGoalsRepository(database = database, clock = { JULY_03 })
            val goal = repository.createGoal(
                profileId,
                CreateSavingsGoalInput(
                    name = "Emergency fund",
                    targetCents = 50_000,
                    currencyCode = "USD",
                    accountId = accountId,
                ),
            )

            val afterDeposit = repository.deposit(
                profileId,
                goal.id,
                SavingsGoalOperationInput(amountCents = 10_000, createdAtEpochMillis = JULY_03),
            )
            val afterWithdraw = repository.withdraw(
                profileId,
                goal.id,
                SavingsGoalOperationInput(amountCents = 4_000, note = "Use", createdAtEpochMillis = JULY_04),
            )

            assertEquals(10_000L, afterDeposit.currentCents)
            assertEquals(6_000L, afterWithdraw.currentCents)
            assertEquals(12.0, afterWithdraw.progressPercent, 0.0)
            assertFalse(afterWithdraw.isCompleted)
            assertEquals(44_000L, afterWithdraw.remainingCents)

            val history = repository.listHistory(profileId, goal.id)
            assertEquals(listOf(SavingsGoalTransactionType.Withdraw, SavingsGoalTransactionType.Deposit), history.map { it.type })
            assertEquals(6_000L, savingsGoalHistoryCurrentCents(history))
            assertEquals(
                savingsGoalHistoryCurrentCents(history),
                database.savingsGoalDao().getById(profileId, goal.id)?.currentCents,
            )

            val transactions = database.transactionDao().listHistory(profileId, limit = 10, offset = 0)
            assertEquals(2, transactions.size)
            val withdrawTransaction = transactions.first()
            val depositTransaction = transactions.last()
            assertEquals("income", withdrawTransaction.type)
            assertEquals(4_000L, withdrawTransaction.amountCents)
            assertEquals("Use", withdrawTransaction.note)
            assertEquals("2026-07-04", withdrawTransaction.snapshotDate)
            assertEquals(savingsCategoryId, withdrawTransaction.categoryId)
            assertEquals(accountId, withdrawTransaction.accountId)
            assertEquals("USD", withdrawTransaction.currencyCode)
            assertEquals("expense", depositTransaction.type)
            assertEquals(10_000L, depositTransaction.amountCents)
            assertEquals("Emergency fund", depositTransaction.note)
            assertEquals("2026-07-03", depositTransaction.snapshotDate)

            assertFailsWithType<SavingsGoalInsufficientFundsException> {
                repository.withdraw(profileId, goal.id, SavingsGoalOperationInput(amountCents = 6_001))
            }
            assertEquals(2, database.transactionDao().listHistory(profileId, limit = 10, offset = 0).size)
            assertEquals(6_000L, repository.getGoal(profileId, goal.id).currentCents)
        } finally {
            database.close()
        }
    }

    @Test
    fun unlinkedGoalDoesNotCreateAccountTransactions() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomSavingsGoalsRepository(database = database, clock = { JULY_03 })
            val goal = repository.createGoal(
                profileId,
                CreateSavingsGoalInput(
                    name = "Cash envelope",
                    targetCents = 20_000,
                    currencyCode = "USD",
                ),
            )

            repository.deposit(profileId, goal.id, SavingsGoalOperationInput(amountCents = 5_000))

            assertEquals(5_000L, repository.getGoal(profileId, goal.id).currentCents)
            assertEquals(1, repository.listHistory(profileId, goal.id).size)
            assertTrue(database.transactionDao().listHistory(profileId, limit = 10, offset = 0).isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun depositDeliversOptInGoalMilestoneAndDoesNotDuplicate() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database, notifyGoalMilestones = true)
            val notifier = RecordingGoalMilestoneNotifier()
            val repository = RoomSavingsGoalsRepository(
                database = database,
                goalMilestoneNotificationProcessor = GoalMilestoneNotificationProcessor(
                    notifier = notifier,
                    clock = { JULY_03 },
                ),
                goalMilestoneNotificationPreferenceProvider = profilePreferenceProvider(database),
                clock = { JULY_03 },
            )
            val goal = repository.createGoal(
                profileId,
                CreateSavingsGoalInput(
                    name = "Trip",
                    targetCents = 10_000,
                    currencyCode = "USD",
                ),
            )

            repository.deposit(profileId, goal.id, SavingsGoalOperationInput(amountCents = 2_500))
            repository.withdraw(profileId, goal.id, SavingsGoalOperationInput(amountCents = 1_000))
            repository.deposit(profileId, goal.id, SavingsGoalOperationInput(amountCents = 1_000))

            assertEquals(1, notifier.requests.size)
            assertEquals("Trip reached 25%", notifier.requests.single().title)
            assertTrue(notifier.requests.single().body.contains("USD 25.00 of USD 100.00"))
        } finally {
            database.close()
        }
    }

    @Test
    fun depositSkipsGoalMilestoneWhenProfilePreferenceIsDisabled() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database, notifyGoalMilestones = false)
            val notifier = RecordingGoalMilestoneNotifier()
            val repository = RoomSavingsGoalsRepository(
                database = database,
                goalMilestoneNotificationProcessor = GoalMilestoneNotificationProcessor(
                    notifier = notifier,
                    clock = { JULY_03 },
                ),
                goalMilestoneNotificationPreferenceProvider = profilePreferenceProvider(database),
                clock = { JULY_03 },
            )
            val goal = repository.createGoal(
                profileId,
                CreateSavingsGoalInput(
                    name = "Trip",
                    targetCents = 10_000,
                    currencyCode = "USD",
                ),
            )

            repository.deposit(profileId, goal.id, SavingsGoalOperationInput(amountCents = 10_000))

            assertEquals(emptyList<MoneyTrackerNotificationRequest>(), notifier.requests)
        } finally {
            database.close()
        }
    }

    @Test
    fun linkedGoalRequiresSavingsCategoryBeforeCreatingTransaction() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val repository = RoomSavingsGoalsRepository(database = database, clock = { JULY_03 })
            val goal = repository.createGoal(
                profileId,
                CreateSavingsGoalInput(
                    name = "Trip",
                    targetCents = 20_000,
                    currencyCode = "USD",
                    accountId = accountId,
                ),
            )

            assertFailsWithType<SavingsGoalCategoryNotFoundException> {
                repository.deposit(profileId, goal.id, SavingsGoalOperationInput(amountCents = 5_000))
            }

            assertEquals(0L, repository.getGoal(profileId, goal.id).currentCents)
            assertTrue(repository.listHistory(profileId, goal.id).isEmpty())
            assertTrue(database.transactionDao().listHistory(profileId, limit = 10, offset = 0).isEmpty())
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
        notifyGoalMilestones: Boolean = false,
    ): Long {
        return database.localProfileDao().insert(
            LocalProfileEntity(
                label = label,
                languageCode = "en",
                notifyGoalMilestones = notifyGoalMilestones,
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

    private suspend fun insertSavingsCategory(
        database: MoneyTrackerDatabase,
        profileId: Long,
    ): Long {
        return database.categoryDao().insert(
            CategoryEntity(
                profileId = profileId,
                name = "Savings",
                icon = "piggy-bank",
                type = "savings",
                color = "#3b82f6",
                isProtected = false,
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    private fun profilePreferenceProvider(
        database: MoneyTrackerDatabase,
    ): GoalMilestoneNotificationPreferenceProvider {
        return GoalMilestoneNotificationPreferenceProvider { profileId ->
            database.localProfileDao().getById(profileId)?.notifyGoalMilestones == true
        }
    }

    private class RecordingGoalMilestoneNotifier : GoalMilestoneNotifier {
        val requests = mutableListOf<MoneyTrackerNotificationRequest>()

        override fun notify(request: MoneyTrackerNotificationRequest): MoneyTrackerNotificationDeliveryResult {
            requests += request
            return MoneyTrackerNotificationDeliveryResult.Delivered
        }
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
        const val TEST_DATABASE_NAME = "money-tracker-savings-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_savings_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_savings_test_key"
        const val NOW = 1_788_200_000_000L
        const val CREATED_AT = 1_786_000_000_000L
        const val JULY_03 = 1_783_036_800_000L
        const val JULY_04 = 1_783_123_200_000L
    }
}
