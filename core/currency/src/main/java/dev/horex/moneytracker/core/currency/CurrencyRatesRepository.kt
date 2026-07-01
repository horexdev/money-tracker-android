package dev.horex.moneytracker.core.currency

interface CurrencyRatesRepository {
    suspend fun saveSnapshot(input: SaveExchangeRateSnapshotInput): ExchangeRateSnapshot

    suspend fun saveSnapshots(inputs: List<SaveExchangeRateSnapshotInput>): List<ExchangeRateSnapshot>

    suspend fun getSnapshot(
        baseCurrency: String,
        targetCurrency: String,
        snapshotDate: String,
    ): ExchangeRateSnapshot?

    suspend fun getLatestSnapshotAtOrBefore(
        baseCurrency: String,
        targetCurrency: String,
        snapshotDate: String,
    ): ExchangeRateSnapshot?

    suspend fun getLatestSnapshotDate(): String

    suspend fun listBaseCurrencies(profileId: Long): List<String>

    suspend fun saveManualOverride(
        profileId: Long,
        input: SaveExchangeRateOverrideInput,
    ): ExchangeRateOverride

    suspend fun listManualOverrides(profileId: Long): List<ExchangeRateOverride>

    suspend fun deleteManualOverride(profileId: Long, overrideId: Long)

    suspend fun getRate(
        profileId: Long,
        baseCurrency: String,
        targetCurrency: String,
        effectiveDate: String,
    ): ResolvedExchangeRate
}
