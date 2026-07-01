package dev.horex.moneytracker.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class AppPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<AppPreferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { it.toAppPreferences() }
        .distinctUntilChanged()

    suspend fun getActiveProfileId(): Long? {
        return preferences.first().activeProfileId
    }

    suspend fun setActiveProfileId(profileId: Long) {
        require(profileId > NO_PROFILE_ID) { "Active profile id must be positive" }
        dataStore.edit { mutablePreferences ->
            mutablePreferences[AppPreferenceKeys.ActiveProfileId] = profileId
        }
    }

    suspend fun clearActiveProfileId() {
        dataStore.edit { mutablePreferences ->
            mutablePreferences.remove(AppPreferenceKeys.ActiveProfileId)
        }
    }

    suspend fun setTheme(theme: AppThemePreference) {
        dataStore.edit { mutablePreferences ->
            mutablePreferences[AppPreferenceKeys.Theme] = theme.persistedValue
        }
    }

    suspend fun setHideAmounts(hidden: Boolean) {
        dataStore.edit { mutablePreferences ->
            mutablePreferences[AppPreferenceKeys.HideAmounts] = hidden
        }
    }

    suspend fun setAnimateNumbers(animate: Boolean?) {
        dataStore.edit { mutablePreferences ->
            if (animate == null) {
                mutablePreferences.remove(AppPreferenceKeys.AnimateNumbers)
            } else {
                mutablePreferences[AppPreferenceKeys.AnimateNumbers] = animate
            }
        }
    }

    private fun Preferences.toAppPreferences(): AppPreferences {
        return AppPreferences(
            activeProfileId = this[AppPreferenceKeys.ActiveProfileId]?.takeIf { it > NO_PROFILE_ID },
            theme = normalizeAppThemePreference(this[AppPreferenceKeys.Theme]),
            hideAmounts = this[AppPreferenceKeys.HideAmounts] ?: false,
            animateNumbers = this[AppPreferenceKeys.AnimateNumbers],
        )
    }

    private companion object {
        const val NO_PROFILE_ID = 0L
    }
}

private object AppPreferenceKeys {
    val ActiveProfileId = longPreferencesKey("active_profile_id")
    val Theme = stringPreferencesKey("theme")
    val HideAmounts = booleanPreferencesKey("hide_amounts")
    val AnimateNumbers = booleanPreferencesKey("animate_numbers")
}
