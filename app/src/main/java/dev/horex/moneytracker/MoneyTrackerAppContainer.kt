package dev.horex.moneytracker

import android.content.Context
import dev.horex.moneytracker.core.accounts.RoomAccountsRepository
import dev.horex.moneytracker.core.balance.RoomBalancesRepository
import dev.horex.moneytracker.core.categories.RoomCategoriesRepository
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.profile.LocalProfileDefaults
import dev.horex.moneytracker.core.database.profile.LocalProfileRepository
import dev.horex.moneytracker.core.database.profile.normalizeLocalProfileLanguageCode
import dev.horex.moneytracker.core.database.seed.DefaultProfileSeedRepository
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import dev.horex.moneytracker.core.notifications.AndroidMoneyTrackerNotificationBuilder
import dev.horex.moneytracker.core.notifications.AndroidMoneyTrackerNotifier
import dev.horex.moneytracker.core.notifications.AndroidNotificationChannelRegistrar
import dev.horex.moneytracker.core.notifications.AndroidNotificationIntentFactory
import dev.horex.moneytracker.core.notifications.AndroidNotificationPermissionController
import dev.horex.moneytracker.core.preferences.AppPreferencesRepository
import dev.horex.moneytracker.core.preferences.DataStoreActiveProfileIdStore
import dev.horex.moneytracker.core.preferences.RoomSettingsRepository
import dev.horex.moneytracker.core.preferences.createAppPreferencesDataStore
import dev.horex.moneytracker.core.stats.RoomStatsRepository
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

    val balancesRepository: RoomBalancesRepository by lazy {
        RoomBalancesRepository(database)
    }

    val categoriesRepository: RoomCategoriesRepository by lazy {
        RoomCategoriesRepository(database)
    }

    val settingsRepository: RoomSettingsRepository by lazy {
        RoomSettingsRepository(
            database = database,
            localProfileBootstrapper = localProfileRepository,
            appPreferencesRepository = appPreferencesRepository,
        )
    }

    val statsRepository: RoomStatsRepository by lazy {
        RoomStatsRepository(database)
    }

    val transactionsRepository: RoomTransactionsRepository by lazy {
        RoomTransactionsRepository(database)
    }

    val notificationPermissionController: AndroidNotificationPermissionController by lazy {
        AndroidNotificationPermissionController(appContext)
    }

    val notificationChannelRegistrar: AndroidNotificationChannelRegistrar by lazy {
        AndroidNotificationChannelRegistrar(appContext)
    }

    val notificationIntentFactory: AndroidNotificationIntentFactory by lazy {
        AndroidNotificationIntentFactory(
            context = appContext,
            launcherActivityClass = MainActivity::class.java,
        )
    }

    val notificationBuilder: AndroidMoneyTrackerNotificationBuilder by lazy {
        AndroidMoneyTrackerNotificationBuilder(
            context = appContext,
            smallIconResId = R.drawable.ic_notification_money_tracker,
        )
    }

    val notificationNotifier: AndroidMoneyTrackerNotifier by lazy {
        AndroidMoneyTrackerNotifier(
            context = appContext,
            channelRegistrar = notificationChannelRegistrar,
            permissionController = notificationPermissionController,
            notificationBuilder = notificationBuilder,
        )
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
