package dev.horex.moneytracker.core.preferences

import androidx.room.withTransaction
import dev.horex.moneytracker.core.currency.IsoCurrencyCatalog
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import java.util.Locale

class RoomSettingsRepository(
    private val database: MoneyTrackerDatabase,
    private val localProfileBootstrapper: LocalProfileBootstrapper,
    private val appPreferencesRepository: AppPreferencesRepository? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SettingsRepository {
    private val localProfileDao = database.localProfileDao()
    private val accountDao = database.accountDao()

    override suspend fun getSettings(): MoneyTrackerSettings {
        val profileId = localProfileBootstrapper.ensureActiveProfile().id
        val profile = localProfileDao.getById(profileId) ?: throw SettingsProfileNotFoundException()
        return profile.toSettings(resolveBaseCurrencyCode(profileId))
    }

    override suspend fun updateSettings(input: UpdateSettingsInput): MoneyTrackerSettings {
        val profileId = localProfileBootstrapper.ensureActiveProfile().id
        val updated = database.withTransaction {
            val existing = localProfileDao.getById(profileId) ?: throw SettingsProfileNotFoundException()
            val updated = existing.applyUpdate(input)
            if (localProfileDao.updateAndReturnCount(updated) != 1) {
                throw SettingsProfileNotFoundException()
            }
            updated
        }

        appPreferencesRepository?.syncUiPreferences(updated)

        return updated.toSettings(resolveBaseCurrencyCode(profileId))
    }

    private suspend fun LocalProfileEntity.applyUpdate(input: UpdateSettingsInput): LocalProfileEntity {
        val notificationInput = input.notificationPreferences
        val uiInput = input.uiPreferences
        return copy(
            languageCode = input.languageCode?.requireSettingsLanguageCode() ?: languageCode,
            displayCurrenciesCsv = input.displayCurrencyCodes?.normalizedDisplayCurrencyCsv()
                ?: displayCurrenciesCsv,
            notifyBudgetAlerts = notificationInput?.notifyBudgetAlerts ?: notifyBudgetAlerts,
            notifyRecurringReminders = notificationInput?.notifyRecurringReminders
                ?: notifyRecurringReminders,
            notifyWeeklySummary = notificationInput?.notifyWeeklySummary ?: notifyWeeklySummary,
            notifyGoalMilestones = notificationInput?.notifyGoalMilestones ?: notifyGoalMilestones,
            statsChartStyle = uiInput?.statsChartStyle?.persistedValue ?: statsChartStyle,
            animateNumbers = when {
                uiInput?.clearAnimateNumbers == true -> null
                uiInput?.animateNumbers != null -> uiInput.animateNumbers
                else -> animateNumbers
            },
            theme = uiInput?.theme?.persistedValue ?: theme,
            hideAmounts = uiInput?.hideAmounts ?: hideAmounts,
            updatedAtEpochMillis = clock(),
        )
    }

    private suspend fun resolveBaseCurrencyCode(profileId: Long): String {
        return accountDao.getDefault(profileId)?.currencyCode ?: DEFAULT_SETTINGS_BASE_CURRENCY
    }
}

private suspend fun AppPreferencesRepository.syncUiPreferences(profile: LocalProfileEntity) {
    setTheme(normalizeAppThemePreference(profile.theme))
    setHideAmounts(profile.hideAmounts)
    setAnimateNumbers(profile.animateNumbers)
}

private fun LocalProfileEntity.toSettings(baseCurrencyCode: String): MoneyTrackerSettings {
    return MoneyTrackerSettings(
        profileId = id,
        baseCurrencyCode = baseCurrencyCode,
        displayCurrencyCodes = displayCurrenciesCsv.toCurrencyCodes(),
        languageCode = normalizeSettingsLanguageCode(languageCode),
        notificationPreferences = SettingsNotificationPreferences(
            notifyBudgetAlerts = notifyBudgetAlerts,
            notifyRecurringReminders = notifyRecurringReminders,
            notifyWeeklySummary = notifyWeeklySummary,
            notifyGoalMilestones = notifyGoalMilestones,
        ),
        uiPreferences = SettingsUiPreferences(
            statsChartStyle = normalizeStatsChartStylePreference(statsChartStyle),
            animateNumbers = animateNumbers,
            theme = normalizeAppThemePreference(theme),
            hideAmounts = hideAmounts,
        ),
    )
}

private fun List<String>.normalizedDisplayCurrencyCsv(): String {
    val normalized = map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotEmpty() }
        .distinct()
    if (normalized.size > MAX_SETTINGS_DISPLAY_CURRENCIES) {
        throw TooManySettingsDisplayCurrenciesException()
    }
    if (normalized.any { !IsoCurrencyCatalog.isSupported(it) }) {
        throw InvalidSettingsCurrencyException()
    }
    return normalized.joinToString(separator = ",")
}

private fun String.toCurrencyCodes(): List<String> {
    return split(',')
        .map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotEmpty() && IsoCurrencyCatalog.isSupported(it) }
        .distinct()
}
