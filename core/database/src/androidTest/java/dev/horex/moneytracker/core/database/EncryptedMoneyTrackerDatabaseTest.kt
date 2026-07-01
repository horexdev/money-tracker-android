package dev.horex.moneytracker.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedMoneyTrackerDatabaseTest {
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
    fun encryptedDatabaseOpensAndReopens() = runBlocking {
        cleanUp()

        val firstDatabase = createDatabase()
        try {
            firstDatabase.localProfileDao().insert(
                LocalProfileEntity(
                    label = "Encrypted profile",
                    languageCode = "ru",
                    createdAtEpochMillis = 1,
                    updatedAtEpochMillis = 1,
                ),
            )
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = createDatabase()
        try {
            val profiles = reopenedDatabase.localProfileDao().list()

            assertEquals(1, profiles.size)
            assertEquals("Encrypted profile", profiles.single().label)
            assertTrue(context.getDatabasePath(TEST_DATABASE_NAME).exists())
        } finally {
            reopenedDatabase.close()
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

    private companion object {
        const val TEST_DATABASE_NAME = "money-tracker-encrypted-test.db"
        const val TEST_PREFERENCES_NAME = "money_tracker_encrypted_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_encrypted_test_key"
    }
}
