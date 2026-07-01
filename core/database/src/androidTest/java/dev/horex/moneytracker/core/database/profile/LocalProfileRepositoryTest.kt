package dev.horex.moneytracker.core.database.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
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
            assertTrue(personal.id != selected.id)
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
