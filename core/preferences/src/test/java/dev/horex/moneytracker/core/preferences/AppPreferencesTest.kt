package dev.horex.moneytracker.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun defaultsDoNotSelectProfileOrOverrideUiPrefs() {
        val preferences = AppPreferences()

        assertNull(preferences.activeProfileId)
        assertEquals(AppThemePreference.System, preferences.theme)
        assertEquals(false, preferences.hideAmounts)
        assertNull(preferences.animateNumbers)
    }

    @Test
    fun themePreferenceNormalizationFallsBackToSystem() {
        assertEquals(AppThemePreference.System, normalizeAppThemePreference(null))
        assertEquals(AppThemePreference.System, normalizeAppThemePreference("unknown"))
        assertEquals(AppThemePreference.System, normalizeAppThemePreference("system"))
        assertEquals(AppThemePreference.Light, normalizeAppThemePreference("light"))
        assertEquals(AppThemePreference.Dark, normalizeAppThemePreference("dark"))
    }
}
