package dev.horex.moneytracker.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.profile.LocalProfileDefaults
import dev.horex.moneytracker.core.database.profile.LocalProfileRepository
import dev.horex.moneytracker.core.database.seed.DefaultProfileSeedRepository
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomSettingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferenceFiles = mutableListOf<File>()

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE_NAME)
        context.getSharedPreferences(TEST_KEY_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferenceFiles.forEach { it.delete() }
        preferenceFiles.clear()
    }

    @Test
    fun getSettingsReturnsProfileAndDerivedBaseCurrency() = runBlocking {
        val holder = createRepository(clock = { 1_000L })
        try {
            val settings = holder.settingsRepository.getSettings()

            assertEquals("ru", settings.languageCode)
            assertEquals("RUB", settings.baseCurrencyCode)
            assertEquals(emptyList<String>(), settings.displayCurrencyCodes)
            assertEquals(SettingsNotificationPreferences(), settings.notificationPreferences)
            assertEquals(SettingsUiPreferences(), settings.uiPreferences)
        } finally {
            holder.close()
        }
    }

    @Test
    fun updateSettingsPersistsProfileSettingsAndMirrorsUiPreferences() = runBlocking {
        var now = 2_000L
        val holder = createRepository(clock = { now })
        try {
            val original = holder.settingsRepository.getSettings()
            now = 3_000L

            val updated = holder.settingsRepository.updateSettings(
                UpdateSettingsInput(
                    displayCurrencyCodes = listOf(" usd ", "eur", "USD"),
                    languageCode = "uk-UA",
                    notificationPreferences = UpdateSettingsNotificationPreferencesInput(
                        notifyBudgetAlerts = false,
                        notifyRecurringReminders = true,
                        notifyWeeklySummary = true,
                        notifyGoalMilestones = true,
                    ),
                    uiPreferences = UpdateSettingsUiPreferencesInput(
                        statsChartStyle = StatsChartStylePreference.DualBar,
                        animateNumbers = false,
                        theme = AppThemePreference.Dark,
                        hideAmounts = true,
                    ),
                ),
            )

            assertEquals(original.profileId, updated.profileId)
            assertEquals(listOf("USD", "EUR"), updated.displayCurrencyCodes)
            assertEquals("uk", updated.languageCode)
            assertEquals(
                SettingsNotificationPreferences(
                    notifyBudgetAlerts = false,
                    notifyRecurringReminders = true,
                    notifyWeeklySummary = true,
                    notifyGoalMilestones = true,
                ),
                updated.notificationPreferences,
            )
            assertEquals(
                SettingsUiPreferences(
                    statsChartStyle = StatsChartStylePreference.DualBar,
                    animateNumbers = false,
                    theme = AppThemePreference.Dark,
                    hideAmounts = true,
                ),
                updated.uiPreferences,
            )

            val profile = checkNotNull(holder.database.localProfileDao().getById(updated.profileId))
            assertEquals("USD,EUR", profile.displayCurrenciesCsv)
            assertEquals("uk", profile.languageCode)
            assertEquals("dual_bar", profile.statsChartStyle)
            assertEquals(3_000L, profile.updatedAtEpochMillis)

            assertEquals(
                AppPreferences(
                    activeProfileId = updated.profileId,
                    theme = AppThemePreference.Dark,
                    hideAmounts = true,
                    animateNumbers = false,
                ),
                holder.appPreferencesRepository.preferences.first(),
            )
        } finally {
            holder.close()
        }
    }

    @Test
    fun updateSettingsCanClearExplicitAnimateNumbersPreference() = runBlocking {
        val holder = createRepository()
        try {
            holder.settingsRepository.updateSettings(
                UpdateSettingsInput(
                    uiPreferences = UpdateSettingsUiPreferencesInput(animateNumbers = true),
                ),
            )

            val cleared = holder.settingsRepository.updateSettings(
                UpdateSettingsInput(
                    uiPreferences = UpdateSettingsUiPreferencesInput(clearAnimateNumbers = true),
                ),
            )

            assertNull(cleared.uiPreferences.animateNumbers)
            assertNull(holder.appPreferencesRepository.preferences.first().animateNumbers)
        } finally {
            holder.close()
        }
    }

    @Test
    fun updateSettingsRejectsInvalidLanguageAndDisplayCurrencies() = runBlocking {
        val holder = createRepository()
        try {
            assertThrowsSettings<InvalidSettingsLanguageException> {
                holder.settingsRepository.updateSettings(UpdateSettingsInput(languageCode = "zh"))
            }
            assertThrowsSettings<InvalidSettingsCurrencyException> {
                holder.settingsRepository.updateSettings(UpdateSettingsInput(displayCurrencyCodes = listOf("FAKE")))
            }
            assertThrowsSettings<TooManySettingsDisplayCurrenciesException> {
                holder.settingsRepository.updateSettings(
                    UpdateSettingsInput(displayCurrencyCodes = listOf("USD", "EUR", "GBP", "JPY")),
                )
            }
        } finally {
            holder.close()
        }
    }

    private fun createRepository(
        clock: () -> Long = { 1_000L },
    ): SettingsRepositoryHolder {
        val database = MoneyTrackerDatabaseFactory.createEncrypted(
            context = context,
            passphraseStore = AndroidDatabasePassphraseStore(
                context = context,
                preferencesName = TEST_KEY_PREFERENCES_NAME,
                keyAlias = TEST_KEY_ALIAS,
            ),
            databaseName = TEST_DATABASE_NAME,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStoreFile = File(context.filesDir, "settings-${System.nanoTime()}.preferences_pb")
            .also { preferenceFiles += it }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { dataStoreFile },
        )
        val appPreferencesRepository = AppPreferencesRepository(dataStore)
        val localProfileRepository = LocalProfileRepository(
            database = database,
            activeProfileIdStore = DataStoreActiveProfileIdStore(appPreferencesRepository),
            defaults = LocalProfileDefaults(languageCode = "ru"),
            profileSeeder = DefaultProfileSeedRepository(
                database = database,
                clock = clock,
            ),
            clock = clock,
        )
        return SettingsRepositoryHolder(
            database = database,
            scope = scope,
            appPreferencesRepository = appPreferencesRepository,
            settingsRepository = RoomSettingsRepository(
                database = database,
                localProfileBootstrapper = localProfileRepository,
                appPreferencesRepository = appPreferencesRepository,
                clock = clock,
            ),
        )
    }

    private inline fun <reified T : Throwable> assertThrowsSettings(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (expected: Throwable) {
            assertEquals(T::class.java, expected::class.java)
        }
    }

    private data class SettingsRepositoryHolder(
        val database: MoneyTrackerDatabase,
        val scope: CoroutineScope,
        val appPreferencesRepository: AppPreferencesRepository,
        val settingsRepository: RoomSettingsRepository,
    ) {
        fun close() {
            scope.cancel()
            database.close()
        }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "money-tracker-settings-test.db"
        const val TEST_KEY_PREFERENCES_NAME = "money_tracker_settings_test_key"
        const val TEST_KEY_ALIAS = "money_tracker_settings_test_key"
    }
}
