package dev.horex.moneytracker.core.currency

import java.util.Locale

const val RATE_SCALE_E8: Long = 100_000_000L

data class CurrencyInfo(
    val code: String,
    val displayName: String,
    val symbol: String,
)

data class ExchangeRateSnapshot(
    val id: Long,
    val snapshotDate: String,
    val baseCurrency: String,
    val targetCurrency: String,
    val rateE8: Long,
    val createdAtEpochMillis: Long,
)

data class ExchangeRateOverride(
    val id: Long,
    val profileId: Long,
    val effectiveDate: String,
    val baseCurrency: String,
    val targetCurrency: String,
    val rateE8: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class ResolvedExchangeRate(
    val profileId: Long,
    val effectiveDate: String,
    val baseCurrency: String,
    val targetCurrency: String,
    val rateE8: Long,
    val source: ExchangeRateSource,
)

enum class ExchangeRateSource {
    SameCurrency,
    ManualOverride,
    Snapshot,
    SystemRate,
}

data class SaveExchangeRateSnapshotInput(
    val snapshotDate: String,
    val baseCurrency: String,
    val targetCurrency: String,
    val rateE8: Long,
)

data class SaveExchangeRateOverrideInput(
    val effectiveDate: String,
    val baseCurrency: String,
    val targetCurrency: String,
    val rateE8: Long,
)

fun CurrencyInfo.currencyDisplayText(): String {
    val symbol = currencySymbolOrNull()
    return if (symbol == null) {
        "$code - $displayName"
    } else {
        "$code - $displayName ($symbol)"
    }
}

fun CurrencyInfo.currencySymbolOrNull(): String? {
    return symbol
        .trim()
        .takeIf { it.isNotEmpty() && it != code }
}

fun CurrencyInfo.matchesCurrencyQuery(
    query: String,
    locale: Locale = Locale.getDefault(),
): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) {
        return true
    }

    val codeQuery = normalized.uppercase(Locale.US)
    val nameQuery = normalized.lowercase(locale)
    return code.contains(codeQuery) || displayName.lowercase(locale).contains(nameQuery)
}
