package dev.horex.moneytracker.core.preferences

import java.util.Locale

const val DEFAULT_SETTINGS_LANGUAGE_CODE = "en"
const val DEFAULT_SETTINGS_BASE_CURRENCY = "USD"
const val MAX_SETTINGS_DISPLAY_CURRENCIES = 3

val SupportedSettingsLanguageCodes = listOf(
    "en",
    "ru",
    "uk",
    "be",
    "kk",
    "uz",
    "es",
    "de",
    "it",
    "fr",
    "pt",
    "nl",
    "ar",
    "tr",
    "ko",
    "ms",
    "id",
)

data class MoneyTrackerSettings(
    val profileId: Long,
    val baseCurrencyCode: String = DEFAULT_SETTINGS_BASE_CURRENCY,
    val displayCurrencyCodes: List<String> = emptyList(),
    val languageCode: String = DEFAULT_SETTINGS_LANGUAGE_CODE,
    val notificationPreferences: SettingsNotificationPreferences = SettingsNotificationPreferences(),
    val uiPreferences: SettingsUiPreferences = SettingsUiPreferences(),
)

data class SettingsNotificationPreferences(
    val notifyBudgetAlerts: Boolean = true,
    val notifyRecurringReminders: Boolean = false,
    val notifyWeeklySummary: Boolean = false,
    val notifyGoalMilestones: Boolean = false,
)

data class SettingsUiPreferences(
    val statsChartStyle: StatsChartStylePreference = StatsChartStylePreference.Donut,
    val animateNumbers: Boolean? = null,
    val theme: AppThemePreference = AppThemePreference.System,
    val hideAmounts: Boolean = false,
)

enum class StatsChartStylePreference(
    val persistedValue: String,
) {
    Donut("donut"),
    StackedBar("stacked_bar"),
    DualBar("dual_bar"),
    ProfitBars("profit_bars"),
}

data class UpdateSettingsInput(
    val displayCurrencyCodes: List<String>? = null,
    val languageCode: String? = null,
    val notificationPreferences: UpdateSettingsNotificationPreferencesInput? = null,
    val uiPreferences: UpdateSettingsUiPreferencesInput? = null,
)

data class UpdateSettingsNotificationPreferencesInput(
    val notifyBudgetAlerts: Boolean? = null,
    val notifyRecurringReminders: Boolean? = null,
    val notifyWeeklySummary: Boolean? = null,
    val notifyGoalMilestones: Boolean? = null,
)

data class UpdateSettingsUiPreferencesInput(
    val statsChartStyle: StatsChartStylePreference? = null,
    val animateNumbers: Boolean? = null,
    val clearAnimateNumbers: Boolean = false,
    val theme: AppThemePreference? = null,
    val hideAmounts: Boolean? = null,
)

fun normalizeStatsChartStylePreference(value: String?): StatsChartStylePreference {
    return StatsChartStylePreference.entries.firstOrNull { it.persistedValue == value }
        ?: StatsChartStylePreference.Donut
}

fun normalizeSettingsLanguageCode(value: String?): String {
    val normalized = value.normalizedSettingsLanguageCode()
    return normalized.takeIf { it in SupportedSettingsLanguageCodes } ?: DEFAULT_SETTINGS_LANGUAGE_CODE
}

fun isSupportedSettingsLanguageCode(value: String?): Boolean {
    return value.normalizedSettingsLanguageCode() in SupportedSettingsLanguageCodes
}

internal fun String?.requireSettingsLanguageCode(): String {
    val normalized = normalizedSettingsLanguageCode()
    if (normalized !in SupportedSettingsLanguageCodes) {
        throw InvalidSettingsLanguageException()
    }
    return normalized
}

private fun String?.normalizedSettingsLanguageCode(): String {
    return this
        ?.trim()
        ?.lowercase(Locale.US)
        ?.substringBefore('-')
        ?.substringBefore('_')
        ?: ""
}
