package dev.horex.moneytracker.core.balance

import dev.horex.moneytracker.core.currency.CurrencyCatalog
import dev.horex.moneytracker.core.currency.CurrencyRatesRepository
import dev.horex.moneytracker.core.currency.ExchangeRateNotFoundException
import dev.horex.moneytracker.core.currency.IsoCurrencyCatalog
import dev.horex.moneytracker.core.currency.RATE_SCALE_E8
import dev.horex.moneytracker.core.currency.RoomCurrencyRatesRepository
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.dao.BalanceCurrencyTotals
import dev.horex.moneytracker.core.database.dao.BalanceLedgerEntry
import dev.horex.moneytracker.core.database.model.AccountEntity
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class RoomBalancesRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val currencyRatesRepository: CurrencyRatesRepository = RoomCurrencyRatesRepository(database, clock),
    private val currencyCatalog: CurrencyCatalog = IsoCurrencyCatalog,
) : BalancesRepository {
    private val accountDao = database.accountDao()
    private val transactionDao = database.transactionDao()

    override suspend fun listAccountBalances(profileId: Long): List<BalanceAccount> {
        return accountDao.listByProfile(profileId).map { account ->
            account.toBalanceAccount(
                balanceCents = accountDao.getBalanceCents(profileId, account.id),
            )
        }
    }

    override suspend fun getBalance(profileId: Long, query: BalanceQuery): BalanceSnapshot {
        val normalizedQuery = query.normalized()
        val accountId = normalizedQuery.accountId
        if (accountId != null && accountDao.getById(profileId, accountId) == null) {
            throw BalanceAccountNotFoundException()
        }

        val baseCurrency = normalizedQuery.baseCurrencyCode
            ?: accountDao.getDefault(profileId)?.currencyCode
            ?: DEFAULT_BASE_CURRENCY
        val accountBalances = listAccountBalances(profileId)
        val includeExcludedAccounts = normalizedQuery.includeExcludedAccounts || accountId != null
        val byCurrency = transactionDao.getBalanceTotalsByCurrency(
            profileId = profileId,
            accountId = accountId,
            includeExcludedAccounts = includeExcludedAccounts,
        ).map(BalanceCurrencyTotals::toBalanceCurrency)
        val ledgerEntries = transactionDao.listBalanceLedgerEntries(
            profileId = profileId,
            accountId = accountId,
            includeExcludedAccounts = includeExcludedAccounts,
        )
        val totalInBaseCents = ledgerEntries.sumInCurrency(
            profileId = profileId,
            targetCurrency = baseCurrency,
        )
        val displayConversions = normalizedQuery.displayCurrencyCodes.map { displayCurrency ->
            DisplayConversion(
                currencyCode = displayCurrency,
                netCents = totalInBaseCents.convertInCurrency(
                    profileId = profileId,
                    baseCurrency = baseCurrency,
                    targetCurrency = displayCurrency,
                    effectiveDate = normalizedQuery.displayConversionDate ?: clock().toUtcDate(),
                ),
            )
        }

        return BalanceSnapshot(
            profileId = profileId,
            accountId = accountId,
            baseCurrencyCode = baseCurrency,
            accountBalances = accountBalances,
            byCurrency = byCurrency,
            displayConversions = displayConversions,
            totalInBaseCents = totalInBaseCents,
        )
    }

    private suspend fun List<BalanceLedgerEntry>.sumInCurrency(
        profileId: Long,
        targetCurrency: String,
    ): Long {
        var total = BigInteger.ZERO
        for (entry in this) {
            val rateE8 = resolveRateE8(
                profileId = profileId,
                baseCurrency = entry.currencyCode,
                targetCurrency = targetCurrency,
                effectiveDate = entry.snapshotDate,
            )
            total = total.add(entry.signedAmountCents.convertByRate(rateE8))
        }
        return total.toLongOrThrow()
    }

    private suspend fun Long.convertInCurrency(
        profileId: Long,
        baseCurrency: String,
        targetCurrency: String,
        effectiveDate: String,
    ): Long {
        val rateE8 = resolveRateE8(
            profileId = profileId,
            baseCurrency = baseCurrency,
            targetCurrency = targetCurrency,
            effectiveDate = effectiveDate,
        )
        return convertByRate(rateE8).toLongOrThrow()
    }

    private suspend fun resolveRateE8(
        profileId: Long,
        baseCurrency: String,
        targetCurrency: String,
        effectiveDate: String,
    ): Long {
        return try {
            currencyRatesRepository.getRate(
                profileId = profileId,
                baseCurrency = baseCurrency,
                targetCurrency = targetCurrency,
                effectiveDate = effectiveDate,
            ).rateE8
        } catch (error: ExchangeRateNotFoundException) {
            throw BalanceExchangeRateNotFoundException()
        }
    }

    private fun BalanceQuery.normalized(): BalanceQuery {
        return copy(
            baseCurrencyCode = baseCurrencyCode?.normalizedCurrencyCode(),
            displayCurrencyCodes = displayCurrencyCodes.mapTo(LinkedHashSet()) {
                it.normalizedCurrencyCode()
            }.toList(),
        )
    }

    private fun String.normalizedCurrencyCode(): String {
        val normalized = trim().uppercase(Locale.US)
        if (!currencyCatalog.isSupported(normalized)) {
            throw InvalidBalanceCurrencyException()
        }
        return normalized
    }
}

private const val DEFAULT_BASE_CURRENCY = "USD"

private val RateScaleBigInteger = BigInteger.valueOf(RATE_SCALE_E8)
private val HalfRateScaleBigInteger = RateScaleBigInteger.divide(BigInteger.valueOf(2))
private val LongMaxBigInteger = BigInteger.valueOf(Long.MAX_VALUE)
private val LongMinBigInteger = BigInteger.valueOf(Long.MIN_VALUE)

private fun BalanceCurrencyTotals.toBalanceCurrency(): BalanceCurrency {
    return BalanceCurrency(
        currencyCode = currencyCode,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
        netCents = incomeCents - expenseCents,
    )
}

private fun AccountEntity.toBalanceAccount(balanceCents: Long): BalanceAccount {
    return BalanceAccount(
        id = id,
        profileId = profileId,
        name = name,
        icon = icon,
        color = color,
        type = type,
        currencyCode = currencyCode,
        isDefault = isDefault,
        includeInTotal = includeInTotal,
        balanceCents = balanceCents,
    )
}

private fun Long.convertByRate(rateE8: Long): BigInteger {
    val product = BigInteger.valueOf(this).multiply(BigInteger.valueOf(rateE8))
    val roundedAbs = product.abs()
        .add(HalfRateScaleBigInteger)
        .divide(RateScaleBigInteger)
    return if (product.signum() < 0) {
        roundedAbs.negate()
    } else {
        roundedAbs
    }
}

private fun BigInteger.toLongOrThrow(): Long {
    if (this > LongMaxBigInteger || this < LongMinBigInteger) {
        throw BalanceAmountOverflowException()
    }
    return toLong()
}

private fun Long.toUtcDate(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}
