package dev.horex.moneytracker.core.database.profile

data class LocalProfile(
    val id: Long,
    val label: String,
    val languageCode: String,
    val displayCurrencies: List<String>,
    val notifyBudgetAlerts: Boolean,
    val notifyRecurringReminders: Boolean,
    val notifyWeeklySummary: Boolean,
    val notifyGoalMilestones: Boolean,
    val statsChartStyle: String,
    val animateNumbers: Boolean?,
    val theme: String,
    val hideAmounts: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class LocalProfileDefaults(
    val label: String = DEFAULT_PROFILE_LABEL,
    val languageCode: String = DEFAULT_LANGUAGE_CODE,
    val displayCurrencies: List<String> = emptyList(),
) {
    init {
        require(label.isNotBlank()) { "Default profile label must not be blank" }
    }

    val normalizedLanguageCode: String = normalizeLocalProfileLanguageCode(languageCode)

    companion object {
        const val DEFAULT_PROFILE_LABEL = "Personal"
        const val DEFAULT_LANGUAGE_CODE = "en"
    }
}

fun normalizeLocalProfileLanguageCode(languageCode: String): String {
    val normalized = languageCode.lowercase().substringBefore('-').substringBefore('_')
    return normalized.takeIf { it in SupportedLocalProfileLanguages } ?: LocalProfileDefaults.DEFAULT_LANGUAGE_CODE
}

private val SupportedLocalProfileLanguages = setOf(
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
