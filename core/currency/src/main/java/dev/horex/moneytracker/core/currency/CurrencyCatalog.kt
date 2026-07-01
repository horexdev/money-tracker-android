package dev.horex.moneytracker.core.currency

import java.util.Currency
import java.util.Locale

interface CurrencyCatalog {
    fun listCurrencies(locale: Locale = Locale.getDefault()): List<CurrencyInfo>

    fun searchCurrencies(
        query: String,
        limit: Int = DEFAULT_CURRENCY_SEARCH_LIMIT,
        locale: Locale = Locale.getDefault(),
    ): List<CurrencyInfo>

    fun isSupported(code: String): Boolean
}

object IsoCurrencyCatalog : CurrencyCatalog {
    override fun listCurrencies(locale: Locale): List<CurrencyInfo> {
        return AvailableCurrencies.map { currency ->
            currency.toCurrencyInfo(locale)
        }
    }

    override fun searchCurrencies(
        query: String,
        limit: Int,
        locale: Locale,
    ): List<CurrencyInfo> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty() || limit <= 0) {
            return emptyList()
        }

        val codeQuery = normalizedQuery.uppercase(Locale.US)
        val nameQuery = normalizedQuery.lowercase(locale)
        val matches = LinkedHashMap<String, CurrencyInfo>()

        listCurrencies(locale)
            .asSequence()
            .filter { it.code.startsWith(codeQuery) }
            .take(limit)
            .forEach { matches[it.code] = it }

        if (matches.size < limit) {
            listCurrencies(locale)
                .asSequence()
                .filter { it.displayName.lowercase(locale).contains(nameQuery) }
                .filterNot { it.code in matches }
                .take(limit - matches.size)
                .forEach { matches[it.code] = it }
        }

        return matches.values.toList()
    }

    override fun isSupported(code: String): Boolean {
        return normalizeCurrencyCode(code) != null
    }

    internal fun normalizeCurrencyCode(code: String): String? {
        val normalized = code.trim().uppercase(Locale.US)
        return normalized
            .takeIf { CurrencyCodePattern.matches(it) }
            ?.takeIf { it in AvailableCurrencyCodes }
    }
}

private fun Currency.toCurrencyInfo(locale: Locale): CurrencyInfo {
    return CurrencyInfo(
        code = currencyCode,
        displayName = getDisplayName(locale),
        symbol = getSymbol(locale),
    )
}

private const val DEFAULT_CURRENCY_SEARCH_LIMIT = 5

private val CurrencyCodePattern = Regex("[A-Z]{3}")

private val ExcludedCurrencyCodes = setOf("XXX", "XTS")

private val AvailableCurrencies: List<Currency> = Currency.getAvailableCurrencies()
    .asSequence()
    .filter { CurrencyCodePattern.matches(it.currencyCode) }
    .filterNot { it.currencyCode in ExcludedCurrencyCodes }
    .sortedBy { it.currencyCode }
    .toList()

private val AvailableCurrencyCodes: Set<String> = AvailableCurrencies
    .mapTo(mutableSetOf()) { it.currencyCode }
