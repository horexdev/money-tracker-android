package dev.horex.moneytracker.core.currency

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.ExchangeRateOverrideEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateSnapshotEntity
import java.time.LocalDate

class RoomCurrencyRatesRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val catalog: IsoCurrencyCatalog = IsoCurrencyCatalog,
) : CurrencyRatesRepository {
    private val profileDao = database.localProfileDao()
    private val snapshotDao = database.exchangeRateSnapshotDao()
    private val overrideDao = database.exchangeRateOverrideDao()

    override suspend fun saveSnapshot(input: SaveExchangeRateSnapshotInput): ExchangeRateSnapshot {
        return saveSnapshots(listOf(input)).single()
    }

    override suspend fun saveSnapshots(inputs: List<SaveExchangeRateSnapshotInput>): List<ExchangeRateSnapshot> {
        if (inputs.isEmpty()) {
            return emptyList()
        }

        val now = clock()
        val normalized = inputs.map { it.normalized() }
        return database.withTransaction {
            snapshotDao.upsertAll(
                normalized.map { input ->
                    ExchangeRateSnapshotEntity(
                        snapshotDate = input.snapshotDate,
                        baseCurrency = input.baseCurrency,
                        targetCurrency = input.targetCurrency,
                        rateE8 = input.rateE8,
                        createdAtEpochMillis = now,
                    )
                },
            )
            normalized.map { input ->
                checkNotNull(
                    snapshotDao.getByDate(
                        baseCurrency = input.baseCurrency,
                        targetCurrency = input.targetCurrency,
                        snapshotDate = input.snapshotDate,
                    ),
                ).toSnapshot()
            }
        }
    }

    override suspend fun getSnapshot(
        baseCurrency: String,
        targetCurrency: String,
        snapshotDate: String,
    ): ExchangeRateSnapshot? {
        val normalizedBase = baseCurrency.normalizedCurrencyCode()
        val normalizedTarget = targetCurrency.normalizedCurrencyCode()
        val normalizedDate = snapshotDate.normalizedIsoDate()
        return snapshotDao.getByDate(
            baseCurrency = normalizedBase,
            targetCurrency = normalizedTarget,
            snapshotDate = normalizedDate,
        )?.toSnapshot()
    }

    override suspend fun getLatestSnapshotAtOrBefore(
        baseCurrency: String,
        targetCurrency: String,
        snapshotDate: String,
    ): ExchangeRateSnapshot? {
        val normalizedBase = baseCurrency.normalizedCurrencyCode()
        val normalizedTarget = targetCurrency.normalizedCurrencyCode()
        val normalizedDate = snapshotDate.normalizedIsoDate()
        return snapshotDao.getLatestAtOrBefore(
            baseCurrency = normalizedBase,
            targetCurrency = normalizedTarget,
            snapshotDate = normalizedDate,
        )?.toSnapshot()
    }

    override suspend fun getLatestSnapshotDate(): String {
        return snapshotDao.getLatestSnapshotDate()
    }

    override suspend fun listBaseCurrencies(profileId: Long): List<String> {
        return snapshotDao.listDistinctBaseCurrencies(profileId)
    }

    override suspend fun listActiveCurrencyCodes(profileId: Long): List<String> {
        val activeCodes = LinkedHashSet<String>()
        snapshotDao.listActiveCurrencyCodes(profileId)
            .mapNotNullTo(activeCodes) { catalog.normalizeCurrencyCode(it) }
        profileDao.getById(profileId)
            ?.displayCurrenciesCsv
            ?.toCurrencyCodes()
            ?.let(activeCodes::addAll)
        return activeCodes.sorted()
    }

    override suspend fun saveManualOverride(
        profileId: Long,
        input: SaveExchangeRateOverrideInput,
        overwriteExisting: Boolean,
    ): ExchangeRateOverride {
        val normalized = input.normalized()
        val now = clock()
        return database.withTransaction {
            val existing = overrideDao.getByDate(
                profileId = profileId,
                baseCurrency = normalized.baseCurrency,
                targetCurrency = normalized.targetCurrency,
                effectiveDate = normalized.effectiveDate,
            )
            if (existing != null && !overwriteExisting) {
                throw ExchangeRateOverrideAlreadyExistsException(existing.toOverride())
            }
            overrideDao.upsert(
                ExchangeRateOverrideEntity(
                    id = existing?.id ?: 0,
                    profileId = profileId,
                    effectiveDate = normalized.effectiveDate,
                    baseCurrency = normalized.baseCurrency,
                    targetCurrency = normalized.targetCurrency,
                    rateE8 = normalized.rateE8,
                    createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                    updatedAtEpochMillis = now,
                ),
            )
            checkNotNull(
                overrideDao.getByDate(
                    profileId = profileId,
                    baseCurrency = normalized.baseCurrency,
                    targetCurrency = normalized.targetCurrency,
                    effectiveDate = normalized.effectiveDate,
                ),
            ).toOverride()
        }
    }

    override suspend fun listManualOverrides(profileId: Long): List<ExchangeRateOverride> {
        return overrideDao.listByProfile(profileId).map { it.toOverride() }
    }

    override suspend fun deleteManualOverride(profileId: Long, overrideId: Long) {
        if (overrideDao.deleteById(profileId, overrideId) != 1) {
            throw ExchangeRateOverrideNotFoundException()
        }
    }

    override suspend fun getRate(
        profileId: Long,
        baseCurrency: String,
        targetCurrency: String,
        effectiveDate: String,
    ): ResolvedExchangeRate {
        val normalizedBase = baseCurrency.normalizedCurrencyCode()
        val normalizedTarget = targetCurrency.normalizedCurrencyCode()
        val normalizedDate = effectiveDate.normalizedIsoDate()

        if (normalizedBase == normalizedTarget) {
            return ResolvedExchangeRate(
                profileId = profileId,
                effectiveDate = normalizedDate,
                baseCurrency = normalizedBase,
                targetCurrency = normalizedTarget,
                rateE8 = RATE_SCALE_E8,
                source = ExchangeRateSource.SameCurrency,
            )
        }

        val override = overrideDao.getLatestAtOrBefore(
            profileId = profileId,
            baseCurrency = normalizedBase,
            targetCurrency = normalizedTarget,
            effectiveDate = normalizedDate,
        )
        if (override != null) {
            return override.toResolvedRate(source = ExchangeRateSource.ManualOverride)
        }

        val snapshot = snapshotDao.getLatestAtOrBefore(
            baseCurrency = normalizedBase,
            targetCurrency = normalizedTarget,
            snapshotDate = normalizedDate,
        )
        if (snapshot != null) {
            return snapshot.toResolvedRate(profileId)
        }

        return ResolvedExchangeRate(
            profileId = profileId,
            effectiveDate = normalizedDate,
            baseCurrency = normalizedBase,
            targetCurrency = normalizedTarget,
            rateE8 = SystemExchangeRates.rateE8(normalizedBase, normalizedTarget),
            source = ExchangeRateSource.SystemRate,
        )
    }

    private fun SaveExchangeRateSnapshotInput.normalized(): SaveExchangeRateSnapshotInput {
        return copy(
            snapshotDate = snapshotDate.normalizedIsoDate(),
            baseCurrency = baseCurrency.normalizedCurrencyCode(),
            targetCurrency = targetCurrency.normalizedCurrencyCode(),
            rateE8 = rateE8.normalizedRateE8(),
        )
    }

    private fun SaveExchangeRateOverrideInput.normalized(): SaveExchangeRateOverrideInput {
        return copy(
            effectiveDate = effectiveDate.normalizedIsoDate(),
            baseCurrency = baseCurrency.normalizedCurrencyCode(),
            targetCurrency = targetCurrency.normalizedCurrencyCode(),
            rateE8 = rateE8.normalizedRateE8(),
        )
    }

    private fun String.normalizedCurrencyCode(): String {
        return catalog.normalizeCurrencyCode(this) ?: throw InvalidCurrencyCodeException()
    }

    private fun String.toCurrencyCodes(): List<String> {
        return split(CurrencyInputSeparator)
            .mapNotNull { catalog.normalizeCurrencyCode(it) }
            .distinct()
    }
}

private val CurrencyInputSeparator = Regex("[,;\\s]+")

private fun ExchangeRateSnapshotEntity.toSnapshot(): ExchangeRateSnapshot {
    return ExchangeRateSnapshot(
        id = id,
        snapshotDate = snapshotDate,
        baseCurrency = baseCurrency,
        targetCurrency = targetCurrency,
        rateE8 = rateE8,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

private fun ExchangeRateOverrideEntity.toOverride(): ExchangeRateOverride {
    return ExchangeRateOverride(
        id = id,
        profileId = profileId,
        effectiveDate = effectiveDate,
        baseCurrency = baseCurrency,
        targetCurrency = targetCurrency,
        rateE8 = rateE8,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun ExchangeRateOverrideEntity.toResolvedRate(source: ExchangeRateSource): ResolvedExchangeRate {
    return ResolvedExchangeRate(
        profileId = profileId,
        effectiveDate = effectiveDate,
        baseCurrency = baseCurrency,
        targetCurrency = targetCurrency,
        rateE8 = rateE8,
        source = source,
    )
}

private fun ExchangeRateSnapshotEntity.toResolvedRate(profileId: Long): ResolvedExchangeRate {
    return ResolvedExchangeRate(
        profileId = profileId,
        effectiveDate = snapshotDate,
        baseCurrency = baseCurrency,
        targetCurrency = targetCurrency,
        rateE8 = rateE8,
        source = ExchangeRateSource.Snapshot,
    )
}

private fun String.normalizedIsoDate(): String {
    return try {
        LocalDate.parse(trim()).toString()
    } catch (error: RuntimeException) {
        throw InvalidExchangeRateDateException()
    }
}

private fun Long.normalizedRateE8(): Long {
    if (this <= 0) {
        throw InvalidExchangeRateException()
    }
    return this
}
