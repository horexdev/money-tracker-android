package dev.horex.moneytracker

import android.content.Context
import dev.horex.moneytracker.core.accounts.RoomAccountsRepository
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.profile.LocalProfileDefaults
import dev.horex.moneytracker.core.database.profile.LocalProfileRepository
import dev.horex.moneytracker.core.database.profile.normalizeLocalProfileLanguageCode
import dev.horex.moneytracker.core.database.seed.DefaultProfileSeedRepository
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import dev.horex.moneytracker.core.preferences.AppPreferencesRepository
import dev.horex.moneytracker.core.preferences.DataStoreActiveProfileIdStore
import dev.horex.moneytracker.core.preferences.createAppPreferencesDataStore
import dev.horex.moneytracker.core.transactions.RoomTransactionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.Locale

internal class MoneyTrackerAppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val appPreferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databaseLazy = lazy {
        MoneyTrackerDatabaseFactory.createEncrypted(
            context = appContext,
            passphraseStore = AndroidDatabasePassphraseStore(appContext),
            databaseName = MoneyTrackerDatabase.DATABASE_NAME,
        )
    }

    private val database: MoneyTrackerDatabase by databaseLazy
    private val appPreferencesDataStore by lazy {
        createAppPreferencesDataStore(
            context = appContext,
            scope = appPreferencesScope,
        )
    }

    val appPreferencesRepository: AppPreferencesRepository by lazy {
        AppPreferencesRepository(appPreferencesDataStore)
    }

    val localProfileRepository: LocalProfileRepository by lazy {
        LocalProfileRepository(
            database = database,
            activeProfileIdStore = DataStoreActiveProfileIdStore(appPreferencesRepository),
            defaults = LocalProfileDefaults(
                languageCode = resolveDeviceLanguageCode(appContext),
            ),
            profileSeeder = DefaultProfileSeedRepository(database),
        )
    }

    val accountsRepository: RoomAccountsRepository by lazy {
        RoomAccountsRepository(database)
    }

    val transactionsRepository: RoomTransactionsRepository by lazy {
        RoomTransactionsRepository(database)
    }

    fun close() {
        appPreferencesScope.cancel()
        if (databaseLazy.isInitialized()) {
            database.close()
        }
    }
}

private fun resolveDeviceLanguageCode(context: Context): String {
    val configuredLanguage = context.resources.configuration.locales[0]?.language
    return normalizeLocalProfileLanguageCode(configuredLanguage ?: Locale.getDefault().language)
}
