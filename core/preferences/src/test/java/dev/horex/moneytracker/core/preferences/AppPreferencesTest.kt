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

    @Test
    fun settingsDefaultsMirrorSourceSettingsWithoutServerOnlyFields() {
        val settings = MoneyTrackerSettings(profileId = 7L)

        assertEquals(7L, settings.profileId)
        assertEquals(DEFAULT_SETTINGS_BASE_CURRENCY, settings.baseCurrencyCode)
        assertEquals(DEFAULT_SETTINGS_LANGUAGE_CODE, settings.languageCode)
        assertEquals(emptyList<String>(), settings.displayCurrencyCodes)
        assertEquals(SettingsNotificationPreferences(), settings.notificationPreferences)
        assertEquals(SettingsUiPreferences(), settings.uiPreferences)

        val fieldNames = MoneyTrackerSettings::class.java.declaredFields
            .map { it.name.lowercase() }
        val forbiddenTokens = listOf(
            "telegram",
            "username",
            "firstname",
            "lastname",
            "initdata",
            "legacy",
            "source",
            "bot",
            "chat",
        )
        forbiddenTokens.forEach { token ->
            assertEquals(false, fieldNames.any { token in it })
        }
    }

    @Test
    fun statsChartStyleNormalizationFallsBackToDonut() {
        assertEquals(StatsChartStylePreference.Donut, normalizeStatsChartStylePreference(null))
        assertEquals(StatsChartStylePreference.Donut, normalizeStatsChartStylePreference("unknown"))
        assertEquals(StatsChartStylePreference.Donut, normalizeStatsChartStylePreference("donut"))
        assertEquals(StatsChartStylePreference.StackedBar, normalizeStatsChartStylePreference("stacked_bar"))
        assertEquals(StatsChartStylePreference.DualBar, normalizeStatsChartStylePreference("dual_bar"))
        assertEquals(StatsChartStylePreference.ProfitBars, normalizeStatsChartStylePreference("profit_bars"))
    }

    @Test
    fun settingsLanguageNormalizationUsesSupportedSourceLanguageSet() {
        assertEquals(17, SupportedSettingsLanguageCodes.size)
        assertEquals("ru", normalizeSettingsLanguageCode("RU"))
        assertEquals("uk", normalizeSettingsLanguageCode("uk-UA"))
        assertEquals("en", normalizeSettingsLanguageCode("zh"))
        assertEquals(true, isSupportedSettingsLanguageCode("id"))
        assertEquals(false, isSupportedSettingsLanguageCode("zh"))
    }
}
