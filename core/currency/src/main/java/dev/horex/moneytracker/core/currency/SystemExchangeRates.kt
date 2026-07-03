package dev.horex.moneytracker.core.currency

import java.math.BigInteger

internal object SystemExchangeRates {
    private val rateScale = BigInteger.valueOf(RATE_SCALE_E8)

    fun rateE8(baseCurrency: String, targetCurrency: String): Long {
        val baseRate = usdQuoteE8(baseCurrency)
        val targetRate = usdQuoteE8(targetCurrency)
        return BigInteger.valueOf(targetRate)
            .multiply(rateScale)
            .add(BigInteger.valueOf(baseRate).divide(BigInteger.valueOf(2)))
            .divide(BigInteger.valueOf(baseRate))
            .toLong()
            .takeIf { it > 0L }
            ?: RATE_SCALE_E8
    }

    private fun usdQuoteE8(currencyCode: String): Long {
        return UsdQuoteRatesE8[currencyCode] ?: RATE_SCALE_E8
    }

    private val UsdQuoteRatesE8 = mapOf(
        "USD" to 100_000_000L,
        "EUR" to 87_535_200L,
        "RUB" to 7_775_546_400L,
        "UAH" to 4_485_534_500L,
        "BYN" to 290_421_000L,
        "KZT" to 47_421_766_600L,
        "UZS" to 1_191_225_053_100L,
        "BRL" to 520_283_500L,
        "SAR" to 375_000_000L,
        "TRY" to 4_672_615_600L,
        "KRW" to 154_390_721_500L,
        "MYR" to 407_923_800L,
        "IDR" to 1_798_944_338_900L,
        "TJS" to 926_550_400L,
        "GBP" to 74_977_200L,
        "JPY" to 16_133_366_700L,
        "CNY" to 679_627_400L,
        "AED" to 367_250_000L,
        "CAD" to 141_884_000L,
        "AUD" to 144_697_600L,
        "CHF" to 80_443_900L,
        "GEL" to 264_018_700L,
        "AMD" to 36_804_589_200L,
        "AZN" to 170_036_100L,
        "KGS" to 8_746_437_100L,
    )
}
