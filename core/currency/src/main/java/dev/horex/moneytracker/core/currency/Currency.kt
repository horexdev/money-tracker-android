package dev.horex.moneytracker.core.currency

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
