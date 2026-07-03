package dev.horex.moneytracker.core.currency

import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SystemExchangeRatesTest {
    @Test
    fun systemQuotesCoverRuntimeSupportedCurrencyCatalog() {
        val missingQuotes = runtimeSupportedCurrencyCodes()
            .filterNot(SystemExchangeRates::hasUsdQuote)

        assertEquals(emptyList<String>(), missingQuotes)
    }

    @Test
    fun resolvesSeedAndDisplayCurrenciesWithoutSnapshotRows() {
        val usdToTjs = SystemExchangeRates.rateE8("USD", "TJS")
        val tjsToUsd = SystemExchangeRates.rateE8("TJS", "USD")

        assertTrue(usdToTjs > RATE_SCALE_E8)
        assertTrue(tjsToUsd in 1 until RATE_SCALE_E8)
    }

    @Test
    fun resolvesEverySupportedCatalogPair() {
        val codes = IsoCurrencyCatalog.listCurrencies(Locale.US).map { it.code }

        codes.forEach { base ->
            codes.forEach { target ->
                val rate = if (base == target) {
                    RATE_SCALE_E8
                } else {
                    SystemExchangeRates.rateE8(base, target)
                }

                assertTrue("$base->$target should have a positive system rate", rate > 0)
            }
        }
    }

    @Test
    fun sameCurrencyRateIsStillHandledOutsideSystemTable() {
        assertEquals(RATE_SCALE_E8, SystemExchangeRates.rateE8("USD", "USD"))
    }

    @Test
    fun tinyCrossCurrencyRateDoesNotFallbackToOneToOneRate() {
        if (!IsoCurrencyCatalog.isSupported("LBP") || !IsoCurrencyCatalog.isSupported("XAU")) {
            return
        }

        val rate = SystemExchangeRates.rateE8("LBP", "XAU")

        assertTrue("LBP->XAU should keep a positive E8 rate", rate > 0)
        assertTrue("LBP->XAU must not fallback to a 1:1 rate", rate < RATE_SCALE_E8)
    }

    @Test
    fun missingQuoteDoesNotFallbackToOneToOneRate() {
        try {
            SystemExchangeRates.rateE8("USD", "ZZZ")
            fail("Missing system quote must not resolve as a 1:1 fallback")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("ZZZ"))
        }
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
