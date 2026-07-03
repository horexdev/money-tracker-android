package dev.horex.moneytracker.core.templates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.TransactionTemplateEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import dev.horex.moneytracker.core.transactions.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTransactionTemplatesRepositoryTest {
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
            val expenseCategoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val incomeCategoryId = insertCategory(database, profileId, name = "Salary", type = "income")
            val otherCategoryId = insertCategory(database, otherProfileId, name = "Other food", type = "expense")
            val repository = RoomTransactionTemplatesRepository(database = database, clock = { NOW })

            val created = repository.createTemplate(
                profileId,
                CreateTransactionTemplateInput(
                    name = "Lunch",
                    type = TransactionType.Expense,
                    amountCents = 1_250,
                    amountMode = TransactionTemplateAmountMode.Fixed,
                    categoryId = expenseCategoryId,
                    accountId = accountId,
                    note = "Lunch",
                ),
            )

            assertEquals(profileId, created.profileId)
            assertEquals("USD", created.currencyCode)
            assertEquals("Food", created.categoryName)
            assertEquals("Card", created.accountName)
            assertEquals(TransactionTemplateAmountMode.Fixed, created.amountMode)
            assertEquals(0, created.sortOrder)
            assertEquals(listOf(created.id), repository.listTemplates(profileId).map { it.id })

            val updated = repository.updateTemplate(
                profileId = profileId,
                templateId = created.id,
                input = UpdateTransactionTemplateInput(
                    name = "Lunch variable",
                    amountCents = 1_500,
                    amountMode = TransactionTemplateAmountMode.Variable,
                    note = "",
                ),
            )

            assertEquals("Lunch variable", updated.name)
            assertEquals(1_500L, updated.amountCents)
            assertEquals(TransactionTemplateAmountMode.Variable, updated.amountMode)
            assertEquals("", updated.note)

            assertFailsWithType<TransactionTemplateNotFoundException> {
                repository.getTemplate(otherProfileId, created.id)
            }
            assertFailsWithType<TransactionTemplateAccountNotFoundException> {
                repository.createTemplate(
                    profileId,
                    CreateTransactionTemplateInput(
                        type = TransactionType.Expense,
                        amountCents = 100,
                        categoryId = expenseCategoryId,
                        accountId = otherAccountId,
                    ),
                )
            }
            assertFailsWithType<TransactionTemplateCategoryNotFoundException> {
                repository.createTemplate(
                    profileId,
                    CreateTransactionTemplateInput(
                        type = TransactionType.Expense,
                        amountCents = 100,
                        categoryId = otherCategoryId,
                        accountId = accountId,
                    ),
                )
            }
            assertFailsWithType<TransactionTemplateCategoryTypeException> {
                repository.createTemplate(
                    profileId,
                    CreateTransactionTemplateInput(
                        type = TransactionType.Expense,
                        amountCents = 100,
                        categoryId = incomeCategoryId,
                        accountId = accountId,
                    ),
                )
            }
            assertFailsWithType<InvalidTransactionTemplateAmountException> {
                repository.updateTemplate(profileId, created.id, UpdateTransactionTemplateInput(amountCents = 0))
            }

            repository.deleteTemplate(profileId, created.id)
            assertFailsWithType<TransactionTemplateNotFoundException> {
                repository.getTemplate(profileId, created.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun listAndReorderUseStableSortOrder() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val categoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val repository = RoomTransactionTemplatesRepository(database = database, clock = { NOW })

            val first = insertTemplate(database, profileId, accountId, categoryId, name = "First", sortOrder = 1, createdAt = 20)
            val second = insertTemplate(database, profileId, accountId, categoryId, name = "Second", sortOrder = 1, createdAt = 10)
            val third = insertTemplate(database, profileId, accountId, categoryId, name = "Third", sortOrder = 3, createdAt = 30)

            assertEquals(listOf(second, first, third), repository.listTemplates(profileId).map { it.id })

            val reordered = repository.reorderTemplates(profileId, listOf(third, second, first))

            assertEquals(listOf(third, second, first), reordered.map { it.id })
            assertEquals(listOf(0, 1, 2), reordered.map { it.sortOrder })
            assertTrue(reordered.all { it.updatedAtEpochMillis == NOW })
            assertFailsWithType<InvalidTransactionTemplateReorderException> {
                repository.reorderTemplates(profileId, listOf(third, second))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun applyFixedAndVariableTemplatesCreateTransactions() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId, name = "Card", currency = "USD")
            val categoryId = insertCategory(database, profileId, name = "Food", type = "expense")
            val repository = RoomTransactionTemplatesRepository(database = database, clock = { NOW })
            val fixed = repository.createTemplate(
                profileId,
                CreateTransactionTemplateInput(
                    name = "Lunch",
                    type = TransactionType.Expense,
                    amountCents = 1_250,
                    amountMode = TransactionTemplateAmountMode.Fixed,
                    categoryId = categoryId,
                    accountId = accountId,
                    note = "Lunch",
                ),
            )
            val variable = repository.createTemplate(
                profileId,
                CreateTransactionTemplateInput(
                    name = "Taxi",
                    type = TransactionType.Expense,
                    amountCents = 1_800,
                    amountMode = TransactionTemplateAmountMode.Variable,
                    categoryId = categoryId,
                    accountId = accountId,
                    note = "Taxi",
                ),
            )

            val fixedTransaction = repository.applyTemplate(
                profileId = profileId,
                templateId = fixed.id,
                input = ApplyTransactionTemplateInput(createdAtEpochMillis = JULY_03),
            )

            assertEquals(1_250L, fixedTransaction.amountCents)
            assertEquals("Lunch", fixedTransaction.note)
            assertEquals("2026-07-03", fixedTransaction.snapshotDate)
            assertFalse(fixedTransaction.isAdjustment)

            assertFailsWithType<InvalidTransactionTemplateAmountException> {
                repository.applyTemplate(profileId, variable.id)
            }
            assertFailsWithType<InvalidTransactionTemplateAmountException> {
                repository.applyTemplate(
                    profileId,
                    variable.id,
                    ApplyTransactionTemplateInput(variableAmountCents = 0),
                )
            }

            val variableTransaction = repository.applyTemplate(
                profileId = profileId,
                templateId = variable.id,
                input = ApplyTransactionTemplateInput(
                    variableAmountCents = 2_200,
                    createdAtEpochMillis = JULY_03,
                ),
            )

            assertEquals(2_200L, variableTransaction.amountCents)
            assertEquals("Taxi", variableTransaction.note)
            assertEquals(2, database.transactionDao().listHistory(profileId, limit = 10, offset = 0).size)
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

    private suspend fun insertTemplate(
        database: MoneyTrackerDatabase,
        profileId: Long,
        accountId: Long,
        categoryId: Long,
        name: String,
        sortOrder: Int,
        createdAt: Long,
    ): Long {
        return database.transactionTemplateDao().insert(
            TransactionTemplateEntity(
                profileId = profileId,
                name = name,
                type = TransactionType.Expense.storageValue,
                amountCents = 100,
                amountFixed = true,
                categoryId = categoryId,
                accountId = accountId,
                currencyCode = "USD",
                note = name,
                sortOrder = sortOrder,
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = createdAt,
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
        const val TEST_DATABASE_NAME = "money-tracker-templates-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_templates_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_templates_test_key"
        const val NOW = 1_788_200_000_000L
        const val JULY_03 = 1_783_036_800_000L
    }
}
