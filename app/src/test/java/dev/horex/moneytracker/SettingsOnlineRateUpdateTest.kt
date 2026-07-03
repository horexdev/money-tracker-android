package dev.horex.moneytracker

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsOnlineRateUpdateTest {
    @Test
    fun onlineRateTargetsUseOnlyActiveProfileCurrencies() {
        val targets = onlineRateTargetCurrencies(
            baseCurrency = "usd",
            activeCurrencyCodes = listOf("USD", "EUR", "TJS", "EUR"),
        )

        assertEquals(listOf("EUR", "TJS"), targets)
    }

    @Test
    fun onlineRateTargetsDoNotExpandToUnusedCatalogCurrencies() {
        val targets = onlineRateTargetCurrencies(
            baseCurrency = "USD",
            activeCurrencyCodes = listOf("USD", "EUR"),
        )

        assertEquals(listOf("EUR"), targets)
    }
}
