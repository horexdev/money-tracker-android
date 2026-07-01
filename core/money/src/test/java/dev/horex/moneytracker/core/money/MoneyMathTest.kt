package dev.horex.moneytracker.core.money

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MoneyMathTest {
    @Test
    fun centsArithmeticUsesCheckedLongOperations() {
        assertEquals(300L, MoneyMath.addCents(100, 200))
        assertEquals(-100L, MoneyMath.subtractCents(100, 200))
        assertEquals(600L, MoneyMath.sumCents(listOf(100, 200, 300)))

        assertFailsWithType<MoneyOverflowException> {
            MoneyMath.addCents(Long.MAX_VALUE, 1)
        }
        assertFailsWithType<MoneyOverflowException> {
            MoneyMath.subtractCents(Long.MIN_VALUE, 1)
        }
    }

    @Test
    fun convertCentsUsesRateE8AndHalfUpRounding() {
        assertEquals(9_200L, MoneyMath.convertCents(10_000, 92_000_000L))
        assertEquals(10_000L, MoneyMath.convertCents(10_000, MoneyMath.RATE_SCALE_E8))
        assertEquals(1L, MoneyMath.convertCents(1, 50_000_000L))
        assertEquals(0L, MoneyMath.convertCents(1, 49_000_000L))
        assertEquals(-1L, MoneyMath.convertCents(-1, 50_000_000L))

        assertFailsWithType<InvalidMoneyAmountException> {
            MoneyMath.convertCents(100, 0)
        }
    }

    @Test
    fun decimalToCentsRoundsPredictably() {
        assertEquals(124L, MoneyMath.decimalToCents(BigDecimal("1.235")))
        assertEquals(123L, MoneyMath.decimalToCents(BigDecimal("1.234")))
        assertEquals(-124L, MoneyMath.decimalToCents(BigDecimal("-1.235")))
    }

    @Test
    fun moneyAmountRequiresSameCurrencyForArithmetic() {
        val usd = MoneyAmount(cents = 100, currencyCode = "USD")
        val moreUsd = MoneyAmount(cents = 50, currencyCode = "USD")

        assertEquals(MoneyAmount(cents = 150, currencyCode = "USD"), usd + moreUsd)
        assertEquals(MoneyAmount(cents = 50, currencyCode = "USD"), usd - moreUsd)

        assertFailsWithType<IllegalArgumentException> {
            usd + MoneyAmount(cents = 10, currencyCode = "EUR")
        }
        assertFailsWithType<IllegalArgumentException> {
            MoneyAmount(cents = 10, currencyCode = "usd")
        }
    }

    private inline fun <reified T : Throwable> assertFailsWithType(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            assertTrue(
                "Expected ${T::class.java.simpleName}, got ${error::class.java.simpleName}",
                error is T,
            )
        }
    }
}
