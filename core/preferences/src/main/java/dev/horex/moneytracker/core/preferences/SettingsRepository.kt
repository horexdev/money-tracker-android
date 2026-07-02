package dev.horex.moneytracker.core.preferences

interface SettingsRepository {
    suspend fun getSettings(): MoneyTrackerSettings

    suspend fun updateSettings(input: UpdateSettingsInput): MoneyTrackerSettings
}
