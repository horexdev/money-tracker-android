package dev.horex.moneytracker.core.database.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.model.SystemCategoryLocalization
import dev.horex.moneytracker.core.database.seed.DefaultProfileSeedRepository
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalProfileRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE_NAME)
        context.getSharedPreferences(TEST_KEY_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(TEST_PROFILE_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun firstLaunchCreatesOfflineProfileAndPersistsLocalSelection() = runBlocking {
        cleanUp()

        val firstDatabase = createDatabase()
        val firstStore = createProfileStore()
        try {
            val repository = LocalProfileRepository(
                database = firstDatabase,
                activeProfileIdStore = firstStore,
                defaults = LocalProfileDefaults(languageCode = "ru"),
                clock = { 1_000L },
            )

            val created = repository.ensureActiveProfile()

            assertTrue(created.id > 0)
            assertEquals("Personal", created.label)
            assertEquals("ru", created.languageCode)
            assertEquals(emptyList<String>(), created.displayCurrencies)
            assertEquals(created.id, firstStore.getActiveProfileId())
            assertNull(created.animateNumbers)
            assertEquals(1_000L, created.createdAtEpochMillis)
            assertEquals(1, firstDatabase.localProfileDao().list().size)
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = createDatabase()
        val reopenedStore = createProfileStore()
        try {
            val repository = LocalProfileRepository(
                database = reopenedDatabase,
                activeProfileIdStore = reopenedStore,
                defaults = LocalProfileDefaults(languageCode = "en"),
                clock = { 2_000L },
            )

            val active = repository.ensureActiveProfile()
            val profiles = repository.listProfiles()

            assertEquals(reopenedStore.getActiveProfileId(), active.id)
            assertEquals(1, profiles.size)
            assertEquals("ru", active.languageCode)
            assertEquals(1_000L, active.createdAtEpochMillis)
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun firstLaunchSeedsDefaultAccountAndCategoriesFromProfileLanguage() = runBlocking {
        cleanUp()

        val database = createDatabase()
        val store = createProfileStore()
        try {
            val repository = LocalProfileRepository(
                database = database,
                activeProfileIdStore = store,
                defaults = LocalProfileDefaults(languageCode = "ru"),
                profileSeeder = DefaultProfileSeedRepository(
                    database = database,
                    clock = { 1_500L },
                ),
                clock = { 1_000L },
            )

            val profile = repository.ensureActiveProfile()

            val accounts = database.accountDao().listByProfile(profile.id)
            assertEquals(1, accounts.size)
            val account = accounts.single()
            assertEquals("\u041e\u0441\u043d\u043e\u0432\u043d\u043e\u0439 \u0441\u0447\u0451\u0442", account.name)
            assertEquals("RUB", account.currencyCode)
            assertEquals("wallet", account.icon)
            assertEquals("checking", account.type)
            assertTrue(account.isDefault)
            assertTrue(account.includeInTotal)
            assertEquals(1_500L, account.createdAtEpochMillis)

            val categories = database.categoryDao().listByProfile(profile.id)
            assertEquals(11, categories.size)
            assertEquals(
                SystemCategoryLocalization.definitions.map { it.localizationKey }.toSet(),
                categories.map { it.localizationKey }.toSet(),
            )
            assertEquals(
                SystemCategoryLocalization.definitions.size,
                SystemCategoryLocalization.definitions.map { it.localizationKey }.toSet().size,
            )
            SystemCategoryLocalization.definitions.forEach { definition ->
                assertEquals(SystemCategoryLocalization.supportedLanguageCodes, definition.names.keys)
            }

            val categoriesByName = categories.associateBy { it.name }
            val food = checkNotNull(categoriesByName["\u0415\u0434\u0430"])
            assertEquals(SystemCategoryLocalization.FOOD, food.localizationKey)
            assertEquals("fork-knife", food.icon)
            assertEquals("expense", food.type)
            assertEquals("#f97316", food.color)

            val savings = checkNotNull(categoriesByName["\u041d\u0430\u043a\u043e\u043f\u043b\u0435\u043d\u0438\u044f"])
            assertEquals(SystemCategoryLocalization.SAVINGS, savings.localizationKey)
            assertEquals("piggy-bank", savings.icon)
            assertEquals("savings", savings.type)
            assertEquals("#3b82f6", savings.color)

            val transfer = checkNotNull(database.categoryDao().getProtectedByType(profile.id, "transfer"))
            assertEquals("Transfer", transfer.name)
            assertEquals(SystemCategoryLocalization.TRANSFER, transfer.localizationKey)
            assertEquals("arrows-left-right", transfer.icon)
            assertEquals("#6366f1", transfer.color)
            assertTrue(transfer.isProtected)

            val adjustment = checkNotNull(database.categoryDao().getProtectedByType(profile.id, "adjustment"))
            assertEquals("Adjustment", adjustment.name)
            assertEquals(SystemCategoryLocalization.ADJUSTMENT, adjustment.localizationKey)
            assertEquals("scales", adjustment.icon)
            assertEquals("#94a3b8", adjustment.color)
            assertTrue(adjustment.isProtected)

            val activeAgain = repository.ensureActiveProfile()

            assertEquals(profile.id, activeAgain.id)
            assertEquals(1, database.accountDao().listByProfile(profile.id).size)
            assertEquals(11, database.categoryDao().listByProfile(profile.id).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun selectActiveProfileIsLocalAndRejectsMissingProfile() = runBlocking {
        cleanUp()

        val database = createDatabase()
        val store = createProfileStore()
        try {
            val repository = LocalProfileRepository(
                database = database,
                activeProfileIdStore = store,
                clock = { 1L },
            )

            val personal = repository.ensureActiveProfile()
            val imported = repository.createProfile(
                label = "Imported profile",
                languageCode = "zz",
            )

            assertEquals("en", imported.languageCode)

            val selected = repository.selectActiveProfile(imported.id)

            assertEquals(imported.id, selected.id)
            assertEquals(imported.id, store.getActiveProfileId())
            assertEquals(2, repository.listProfiles().size)
            assertEquals(1, database.accountDao().listByProfile(imported.id).size)
            assertEquals(11, database.categoryDao().listByProfile(imported.id).size)
            assertTrue(personal.id != selected.id)
        } finally {
            database.close()
        }
    }

    @Test
    fun updateProfileLabelAndResetActiveProfileStayLocal() = runBlocking {
        cleanUp()

        val database = createDatabase()
        val store = createProfileStore()
        try {
            val repository = LocalProfileRepository(
                database = database,
                activeProfileIdStore = store,
                defaults = LocalProfileDefaults(languageCode = "ru"),
                profileSeeder = DefaultProfileSeedRepository(
                    database = database,
                    clock = { 2_000L },
                ),
                clock = { 1_000L },
            )

            val active = repository.ensureActiveProfile()
            val renamed = repository.updateProfileLabel(active.id, "Family")

            assertEquals(active.id, renamed.id)
            assertEquals("Family", renamed.label)
            assertEquals(active.id, store.getActiveProfileId())

            val reset = repository.resetActiveProfileData()

            assertTrue(reset.id != active.id)
            assertEquals("Personal", reset.label)
            assertEquals("ru", reset.languageCode)
            assertEquals(reset.id, store.getActiveProfileId())
            assertNull(database.localProfileDao().getById(active.id))
            assertEquals(1, database.accountDao().listByProfile(reset.id).size)
            assertEquals(11, database.categoryDao().listByProfile(reset.id).size)
        } finally {
            database.close()
        }
    }

    private fun createDatabase(): MoneyTrackerDatabase {
        return MoneyTrackerDatabaseFactory.createEncrypted(
            context = context,
            passphraseStore = AndroidDatabasePassphraseStore(
                context = context,
                preferencesName = TEST_KEY_PREFERENCES_NAME,
                keyAlias = TEST_KEY_ALIAS,
            ),
            databaseName = TEST_DATABASE_NAME,
        )
    }

    private fun createProfileStore(): AndroidActiveProfileIdStore {
        return AndroidActiveProfileIdStore(
            context = context,
            preferencesName = TEST_PROFILE_PREFERENCES_NAME,
        )
    }

    private companion object {
        const val TEST_DATABASE_NAME = "money-tracker-local-profile-test.db"
        const val TEST_KEY_PREFERENCES_NAME = "money_tracker_local_profile_test_key"
        const val TEST_PROFILE_PREFERENCES_NAME = "money_tracker_local_profile_test_state"
        const val TEST_KEY_ALIAS = "money_tracker_local_profile_test_key"
    }
}
