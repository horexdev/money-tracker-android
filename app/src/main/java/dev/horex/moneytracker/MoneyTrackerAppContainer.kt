package dev.horex.moneytracker

import android.content.Context
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.profile.AndroidActiveProfileIdStore
import dev.horex.moneytracker.core.database.profile.LocalProfileDefaults
import dev.horex.moneytracker.core.database.profile.LocalProfileRepository
import dev.horex.moneytracker.core.database.profile.normalizeLocalProfileLanguageCode
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import java.util.Locale

internal class MoneyTrackerAppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val databaseLazy = lazy {
        MoneyTrackerDatabaseFactory.createEncrypted(
            context = appContext,
            passphraseStore = AndroidDatabasePassphraseStore(appContext),
            databaseName = MoneyTrackerDatabase.DATABASE_NAME,
        )
    }

    private val database: MoneyTrackerDatabase by databaseLazy

    val localProfileRepository: LocalProfileRepository by lazy {
        LocalProfileRepository(
            database = database,
            activeProfileIdStore = AndroidActiveProfileIdStore(appContext),
            defaults = LocalProfileDefaults(
                languageCode = resolveDeviceLanguageCode(appContext),
            ),
        )
    }

    fun close() {
        if (databaseLazy.isInitialized()) {
            database.close()
        }
    }
}

private fun resolveDeviceLanguageCode(context: Context): String {
    val configuredLanguage = context.resources.configuration.locales[0]?.language
    return normalizeLocalProfileLanguageCode(configuredLanguage ?: Locale.getDefault().language)
}
