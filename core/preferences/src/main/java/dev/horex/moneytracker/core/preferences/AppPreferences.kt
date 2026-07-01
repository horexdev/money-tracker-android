package dev.horex.moneytracker.core.preferences

data class AppPreferences(
    val activeProfileId: Long? = null,
    val theme: AppThemePreference = AppThemePreference.System,
    val hideAmounts: Boolean = false,
    val animateNumbers: Boolean? = null,
)

enum class AppThemePreference(
    val persistedValue: String,
) {
    System("system"),
    Light("light"),
    Dark("dark"),
}

fun normalizeAppThemePreference(value: String?): AppThemePreference {
    return AppThemePreference.entries.firstOrNull { it.persistedValue == value } ?: AppThemePreference.System
}
