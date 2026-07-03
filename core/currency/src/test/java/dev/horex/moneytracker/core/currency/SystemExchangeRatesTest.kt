package dev.horex.moneytracker.core.currency

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SystemExchangeRatesTest {
    @Test
    fun systemQuotesCoverSupportedCurrencyCatalog() {
        val catalogCodes = IsoCurrencyCatalog.listCurrencies(Locale.US).map { it.code }.toSet()
        val missingQuotes = catalogCodes.filterNot(SystemExchangeRates::hasUsdQuote)

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
    fun missingQuoteDoesNotFallbackToOneToOneRate() {
        try {
            SystemExchangeRates.rateE8("USD", "ZZZ")
            fail("Missing system quote must not resolve as a 1:1 fallback")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("ZZZ"))
        }
    }
}
