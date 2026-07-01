package dev.horex.moneytracker.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun createAppPreferencesDataStore(
    context: Context,
    name: String = DEFAULT_APP_PREFERENCES_NAME,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
): DataStore<Preferences> {
    val appContext = context.applicationContext
    return PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { appContext.preferencesDataStoreFile(name) },
    )
}

const val DEFAULT_APP_PREFERENCES_NAME = "money_tracker_app_preferences"
