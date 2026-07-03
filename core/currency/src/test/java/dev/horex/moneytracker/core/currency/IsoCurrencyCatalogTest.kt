package dev.horex.moneytracker.core.currency

import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoCurrencyCatalogTest {
    @Test
    fun validatesKnownIsoCurrencies() {
        assertTrue(IsoCurrencyCatalog.isSupported("USD"))
        assertTrue(IsoCurrencyCatalog.isSupported(" eur "))
        assertTrue(IsoCurrencyCatalog.isSupported("TJS"))
        assertTrue(IsoCurrencyCatalog.isSupported("ADP"))
        assertFalse(IsoCurrencyCatalog.isSupported("XYZ"))
        assertFalse(IsoCurrencyCatalog.isSupported("XXX"))
        assertFalse(IsoCurrencyCatalog.isSupported("XTS"))
        assertFalse(IsoCurrencyCatalog.isSupported(""))
    }

    @Test
    fun listsRuntimeIsoCurrenciesWithDisplayMetadataExcludingPseudoCodes() {
        val currencies = IsoCurrencyCatalog.listCurrencies(locale = Locale.US)
        val codes = currencies.map { it.code }

        assertEquals(runtimeSupportedCurrencyCodes(), codes)
        assertTrue("USD should be available in the runtime currency catalog", "USD" in codes)
        assertTrue("EUR should be available in the runtime currency catalog", "EUR" in codes)
        assertTrue("ADP should be available when present in the runtime currency catalog", "ADP" in codes)
        assertFalse("Pseudo no-currency code should be excluded", "XXX" in codes)
        assertFalse("Test currency code should be excluded", "XTS" in codes)
        assertEquals(codes.sorted(), codes)
        assertTrue(codes.all(SystemExchangeRates::hasUsdQuote))
        assertTrue(currencies.any { it.code == "USD" && it.displayName.isNotBlank() })
    }

    @Test
    fun searchRanksCodePrefixesFirstAndCapsResults() {
        val results = IsoCurrencyCatalog.searchCurrencies("US", locale = Locale.US)

        assertTrue(results.isNotEmpty())
        assertEquals("USD", results.first().code)
        assertTrue(results.size <= 5)
    }

    @Test
    fun searchMatchesDisplayNameWithoutDuplicates() {
        val results = IsoCurrencyCatalog.searchCurrencies("Dollar", locale = Locale.US)
        val codes = results.map { it.code }

        assertTrue(results.any { it.displayName.contains("Dollar") })
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun currencyInfoMatchesCodeAndLocalizedNameQueries() {
        val usd = IsoCurrencyCatalog.listCurrencies(locale = Locale.US)
            .single { it.code == "USD" }

        assertTrue(usd.matchesCurrencyQuery("usd", locale = Locale.US))
        assertTrue(usd.matchesCurrencyQuery("dollar", locale = Locale.US))
        assertTrue(usd.currencyDisplayText().startsWith("USD - "))
    }

    private fun runtimeSupportedCurrencyCodes(): List<String> {
        return Currency.getAvailableCurrencies()
            .asSequence()
            .map { it.currencyCode }
            .filter { CurrencyCodePattern.matches(it) }
            .filterNot { it in ExcludedCurrencyCodes }
            .sorted()
            .toList()
    }

    private companion object {
        private val CurrencyCodePattern = Regex("[A-Z]{3}")
        private val ExcludedCurrencyCodes = setOf("XXX", "XTS")
    }
}
