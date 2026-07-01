package dev.horex.moneytracker.core.currency

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
        assertFalse(IsoCurrencyCatalog.isSupported("XYZ"))
        assertFalse(IsoCurrencyCatalog.isSupported("XXX"))
        assertFalse(IsoCurrencyCatalog.isSupported(""))
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
}
