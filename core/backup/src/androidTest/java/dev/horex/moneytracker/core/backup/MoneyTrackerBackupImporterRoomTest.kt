package dev.horex.moneytracker.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyTrackerBackupImporterRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(
        context,
        MoneyTrackerDatabase::class.java,
    ).build()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomTransactionRollsBackProfileAndSnapshotsWhenImportFails() = runBlocking {
        val importer = MoneyTrackerBackupImporter(database)
        val backup = MoneyTrackerBackup(
            createdAtEpochMillis = TEST_TIME,
            profiles = listOf(profileWithDuplicateLocalAccountNames()),
            exchangeRateSnapshots = listOf(sampleExchangeRateSnapshot()),
        )

        try {
            importer.importBackup(backup)
            fail("Import should fail on duplicate local account names.")
        } catch (_: Exception) {
            // Expected constraint failure should leave the transaction rolled back.
        }

        assertTrue(database.localProfileDao().list().isEmpty())
        assertNull(
            database.exchangeRateSnapshotDao().getByDate(
                baseCurrency = "USD",
                targetCurrency = "EUR",
                snapshotDate = "2026-07-01",
            ),
        )
    }

    private fun profileWithDuplicateLocalAccountNames(): BackupProfile {
        return BackupProfile(
            ref = "profile:main",
            label = "Main",
            languageCode = "en",
            createdAtEpochMillis = TEST_TIME,
            updatedAtEpochMillis = TEST_TIME,
            accounts = listOf(
                sampleAccount(ref = "account:cash", name = "Duplicate"),
                sampleAccount(ref = "account:savings", name = "Duplicate"),
            ),
        )
    }

    private fun sampleAccount(ref: String, name: String): BackupAccount {
        return BackupAccount(
            ref = ref,
            name = name,
            icon = "wallet",
            color = "#6366f1",
            type = BackupAccountType.Checking,
            currencyCode = "USD",
            isDefault = ref == "account:cash",
            includeInTotal = true,
            createdAtEpochMillis = TEST_TIME,
            updatedAtEpochMillis = TEST_TIME,
        )
    }

    private fun sampleExchangeRateSnapshot(): BackupExchangeRateSnapshot {
        return BackupExchangeRateSnapshot(
            snapshotDate = "2026-07-01",
            baseCurrencyCode = "USD",
            targetCurrencyCode = "EUR",
            rateE8 = 92_000_000,
            createdAtEpochMillis = TEST_TIME,
        )
    }

    private companion object {
        const val TEST_TIME = 1_788_200_000_000L
    }
}
