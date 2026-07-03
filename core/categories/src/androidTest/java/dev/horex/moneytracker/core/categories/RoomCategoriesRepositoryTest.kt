package dev.horex.moneytracker.core.categories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.SystemCategoryLocalization
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCategoriesRepositoryTest {
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
    fun createCategoryNormalizesInputAndDefaults() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomCategoriesRepository(database, clock = { 10L })

            val created = repository.createCategory(
                profileId,
                CreateCategoryInput(
                    name = "  Groceries  ",
                    icon = " ",
                    type = CategoryType.Expense,
                    color = " ",
                ),
            )

            assertEquals("Groceries", created.name)
            assertEquals(DEFAULT_CATEGORY_ICON, created.icon)
            assertEquals(DEFAULT_CATEGORY_COLOR, created.color)
            assertEquals(CategoryType.Expense, created.type)
            assertFalse(created.isProtected)
            assertNull(created.deletedAtEpochMillis)
            assertEquals(10L, created.updatedAtEpochMillis)
            assertTrue(repository.hasEditableCategories(profileId))

            assertFailsWithType<CategoryNameEmptyException> {
                repository.createCategory(profileId, CreateCategoryInput(name = " "))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun listCategoriesFiltersTypeAndExcludesProtectedInfrastructure() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomCategoriesRepository(database)
            val both = repository.createCategory(profileId, CreateCategoryInput("Both", type = CategoryType.Both))
            val expense = repository.createCategory(profileId, CreateCategoryInput("Expense", type = CategoryType.Expense))
            val income = repository.createCategory(profileId, CreateCategoryInput("Income", type = CategoryType.Income))
            val savings = repository.createCategory(profileId, CreateCategoryInput("Savings", type = CategoryType.Savings))
            val transferId = insertProtectedCategory(database, profileId, CategoryType.Transfer, "Transfer")
            val accidentalTransferId = database.categoryDao().insert(
                CategoryEntity(
                    profileId = profileId,
                    name = "Accidental transfer",
                    type = CategoryType.Transfer.storageValue,
                    updatedAtEpochMillis = 1L,
                ),
            )

            assertEquals(
                listOf(both.id, expense.id, income.id, savings.id),
                repository.listCategories(profileId).map { it.id },
            )
            assertEquals(
                listOf(both.id, expense.id),
                repository.listCategories(profileId, type = CategoryType.Expense).map { it.id },
            )
            assertEquals(
                listOf(both.id, income.id),
                repository.listCategories(profileId, type = CategoryType.Income).map { it.id },
            )
            assertEquals(
                listOf(savings.id),
                repository.listCategories(profileId, type = CategoryType.Savings).map { it.id },
            )
            assertFalse(repository.listCategories(profileId).any { it.id == transferId })
            assertFalse(repository.listCategories(profileId).any { it.id == accidentalTransferId })
            assertEquals(
                listOf(accidentalTransferId, both.id, expense.id, income.id, savings.id, transferId),
                repository.listAllCategories(profileId).map { it.id },
            )
            assertEquals(
                listOf(accidentalTransferId, transferId),
                repository.listAllCategories(profileId, type = CategoryType.Transfer).map { it.id },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun updateCategoryChangesMutableFieldsOnlyForActiveEditableCategory() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomCategoriesRepository(database, clock = { 20L })
            val category = repository.createCategory(profileId, CreateCategoryInput("Food"))

            val updated = repository.updateCategory(
                profileId = profileId,
                categoryId = category.id,
                input = UpdateCategoryInput(
                    name = "Restaurants",
                    icon = "fork-knife",
                    type = CategoryType.Expense,
                    color = "#f97316",
                ),
            )

            assertEquals("Restaurants", updated.name)
            assertEquals("fork-knife", updated.icon)
            assertEquals(CategoryType.Expense, updated.type)
            assertEquals("#f97316", updated.color)
            assertEquals(20L, updated.updatedAtEpochMillis)

            assertFailsWithType<CategoryProtectedException> {
                repository.updateCategory(
                    profileId,
                    category.id,
                    UpdateCategoryInput(type = CategoryType.Adjustment),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun systemCategoryNamesFollowProfileLanguageUntilUserRename() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database, languageCode = "en")
            val definition = SystemCategoryLocalization.requireDefinition(SystemCategoryLocalization.FOOD)
            val categoryId = database.categoryDao().insert(
                CategoryEntity(
                    profileId = profileId,
                    name = definition.nameForLanguage("en"),
                    localizationKey = definition.localizationKey,
                    icon = definition.icon,
                    type = definition.type,
                    color = definition.color,
                    updatedAtEpochMillis = 1L,
                ),
            )
            val repository = RoomCategoriesRepository(database, clock = { 20L })

            assertEquals(definition.nameForLanguage("en"), repository.getCategory(profileId, categoryId).name)

            updateProfileLanguage(database, profileId, "ru")

            val localized = repository.getCategory(profileId, categoryId)
            assertEquals(definition.nameForLanguage("ru"), localized.name)
            assertEquals(SystemCategoryLocalization.FOOD, localized.localizationKey)

            val unchangedName = repository.updateCategory(
                profileId = profileId,
                categoryId = categoryId,
                input = UpdateCategoryInput(
                    name = definition.nameForLanguage("ru"),
                    color = "#22c55e",
                ),
            )
            assertEquals(definition.nameForLanguage("ru"), unchangedName.name)
            assertEquals(SystemCategoryLocalization.FOOD, unchangedName.localizationKey)

            val renamed = repository.updateCategory(
                profileId = profileId,
                categoryId = categoryId,
                input = UpdateCategoryInput(name = "Groceries"),
            )
            assertEquals("Groceries", renamed.name)
            assertNull(renamed.localizationKey)

            updateProfileLanguage(database, profileId, "es")

            val afterLanguageChange = repository.getCategory(profileId, categoryId)
            assertEquals("Groceries", afterLanguageChange.name)
            assertNull(afterLanguageChange.localizationKey)
            assertNull(database.categoryDao().getById(profileId, categoryId)?.localizationKey)
        } finally {
            database.close()
        }
    }

    @Test
    fun createCategoryRejectsInfrastructureTypes() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomCategoriesRepository(database)

            assertFailsWithType<CategoryProtectedException> {
                repository.createCategory(
                    profileId,
                    CreateCategoryInput("Transfer", type = CategoryType.Transfer),
                )
            }
            assertFailsWithType<CategoryProtectedException> {
                repository.createCategory(
                    profileId,
                    CreateCategoryInput("Adjustment", type = CategoryType.Adjustment),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun protectedCategoriesCanBeReadByTypeButCannotBeEditedOrDeleted() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomCategoriesRepository(database)
            val transferId = insertProtectedCategory(database, profileId, CategoryType.Transfer, "Transfer")

            val transfer = repository.getProtectedCategoryByType(profileId, CategoryType.Transfer)

            assertEquals(transferId, transfer.id)
            assertTrue(transfer.isProtected)
            assertFailsWithType<CategoryProtectedException> {
                repository.updateCategory(profileId, transferId, UpdateCategoryInput(name = "Move"))
            }
            assertFailsWithType<CategoryProtectedException> {
                repository.deleteCategory(profileId, transferId)
            }
            assertFalse(repository.hasEditableCategories(profileId))
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteCategorySoftDeletesAndHidesFromEditableLists() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val repository = RoomCategoriesRepository(database, clock = { 30L })
            val category = repository.createCategory(profileId, CreateCategoryInput("Travel"))

            repository.deleteCategory(profileId, category.id)

            val deleted = repository.getCategory(profileId, category.id)
            assertTrue(deleted.isDeleted)
            assertEquals(30L, deleted.deletedAtEpochMillis)
            assertEquals(30L, deleted.updatedAtEpochMillis)
            assertFalse(repository.hasEditableCategories(profileId))
            assertEquals(emptyList<Long>(), repository.listCategories(profileId).map { it.id })
            assertFailsWithType<CategoryNotFoundException> {
                repository.updateCategory(profileId, category.id, UpdateCategoryInput(name = "Trips"))
            }
            assertFailsWithType<CategoryNotFoundException> {
                repository.deleteCategory(profileId, category.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteReferencedCategoryKeepsTransactionHistoryIntact() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val accountId = insertAccount(database, profileId)
            val repository = RoomCategoriesRepository(database, clock = { 40L })
            val category = repository.createCategory(profileId, CreateCategoryInput("Food", type = CategoryType.Expense))
            val transactionId = insertTransaction(database, profileId, accountId, category.id)

            repository.deleteCategory(profileId, category.id)

            val transaction = database.transactionDao().getById(profileId, transactionId)
            assertNotNull(transaction)
            assertEquals(category.id, transaction!!.categoryId)
            assertTrue(repository.getCategory(profileId, category.id).isDeleted)
        } finally {
            database.close()
        }
    }

    @Test
    fun frequencySortingUsesProfileScopedNonAdjustmentTransactions() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val accountId = insertAccount(database, profileId)
            val otherAccountId = insertAccount(database, otherProfileId)
            val repository = RoomCategoriesRepository(database)
            val coffee = repository.createCategory(profileId, CreateCategoryInput("Coffee", type = CategoryType.Expense))
            val food = repository.createCategory(profileId, CreateCategoryInput("Food", type = CategoryType.Expense))
            val other = repository.createCategory(profileId, CreateCategoryInput("Other", type = CategoryType.Both))
            val otherProfileCategory = repository.createCategory(
                otherProfileId,
                CreateCategoryInput("Food", type = CategoryType.Expense),
            )

            insertTransaction(database, profileId, accountId, food.id, createdAt = 1L)
            insertTransaction(database, profileId, accountId, food.id, createdAt = 2L)
            insertTransaction(database, profileId, accountId, coffee.id, createdAt = 3L)
            insertTransaction(database, profileId, accountId, other.id, isAdjustment = true, createdAt = 4L)
            repeat(5) { index ->
                insertTransaction(
                    database = database,
                    profileId = otherProfileId,
                    accountId = otherAccountId,
                    categoryId = otherProfileCategory.id,
                    createdAt = 10L + index,
                )
            }

            assertEquals(
                listOf(food.id, coffee.id, other.id),
                repository.listCategories(
                    profileId,
                    type = CategoryType.Expense,
                    sortOrder = CategorySortOrder.Frequency,
                ).map { it.id },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun categoryOperationsAreScopedToProfile() = runBlocking {
        cleanUp()
        val database = createDatabase()
        try {
            val profileId = insertProfile(database)
            val otherProfileId = insertProfile(database, label = "Other")
            val repository = RoomCategoriesRepository(database)
            val otherCategory = repository.createCategory(
                otherProfileId,
                CreateCategoryInput("Other profile", type = CategoryType.Expense),
            )

            assertEquals(emptyList<Long>(), repository.listCategories(profileId).map { it.id })
            assertFailsWithType<CategoryNotFoundException> {
                repository.getCategory(profileId, otherCategory.id)
            }
            assertFailsWithType<CategoryNotFoundException> {
                repository.updateCategory(profileId, otherCategory.id, UpdateCategoryInput(name = "Leak"))
            }
            assertFailsWithType<CategoryNotFoundException> {
                repository.deleteCategory(profileId, otherCategory.id)
            }
            assertFalse(repository.getCategory(otherProfileId, otherCategory.id).isDeleted)
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
        languageCode: String = "en",
    ): Long {
        return database.localProfileDao().insert(
            LocalProfileEntity(
                label = label,
                languageCode = languageCode,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    private suspend fun updateProfileLanguage(
        database: MoneyTrackerDatabase,
        profileId: Long,
        languageCode: String,
    ) {
        val profile = checkNotNull(database.localProfileDao().getById(profileId))
        database.localProfileDao().update(
            profile.copy(
                languageCode = languageCode,
                updatedAtEpochMillis = profile.updatedAtEpochMillis + 1,
            ),
        )
    }

    private suspend fun insertAccount(database: MoneyTrackerDatabase, profileId: Long): Long {
        return database.accountDao().insert(
            AccountEntity(
                profileId = profileId,
                name = "Account-$profileId-${System.nanoTime()}",
                currencyCode = "USD",
                isDefault = true,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    private suspend fun insertProtectedCategory(
        database: MoneyTrackerDatabase,
        profileId: Long,
        type: CategoryType,
        name: String,
    ): Long {
        return database.categoryDao().insert(
            CategoryEntity(
                profileId = profileId,
                name = name,
                icon = "shield",
                type = type.storageValue,
                color = "#64748b",
                isProtected = true,
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    private suspend fun insertTransaction(
        database: MoneyTrackerDatabase,
        profileId: Long,
        accountId: Long,
        categoryId: Long,
        isAdjustment: Boolean = false,
        createdAt: Long = 1L,
    ): Long {
        return database.transactionDao().insert(
            TransactionEntity(
                profileId = profileId,
                type = "expense",
                amountCents = 100,
                categoryId = categoryId,
                accountId = accountId,
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
        const val TEST_DATABASE_NAME = "money-tracker-categories-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_categories_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_categories_test_key"
    }
}
