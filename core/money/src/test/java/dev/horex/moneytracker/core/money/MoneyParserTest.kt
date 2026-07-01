package dev.horex.moneytracker.core.money

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MoneyParserTest {
    @Test
    fun parsePositiveCentsAcceptsValidDecimalAmounts() {
        mapOf(
            "12.50" to 1_250L,
            "12" to 1_200L,
            "0.01" to 1L,
            "100.00" to 10_000L,
            "1.5" to 150L,
            " 12.34 " to 1_234L,
            "12,34" to 1_234L,
        ).forEach { (input, expected) ->
            assertEquals(expected, MoneyParser.parsePositiveCents(input))
        }
    }

    @Test
    fun parsePositiveCentsRejectsInvalidOrZeroAmounts() {
        listOf(
            "0",
            "0.00",
            "-5",
            "abc",
            "12.999",
            "",
            "12.5.3",
            ".50",
        ).forEach { input ->
            assertFailsWithType<InvalidMoneyAmountException> {
                MoneyParser.parsePositiveCents(input)
            }
        }
    }

    @Test
    fun parseCentsOrZeroIsLenientAndRoundsToNearestCent() {
        assertEquals(150_050L, MoneyParser.parseCentsOrZero("1500.50"))
        assertEquals(10_000L, MoneyParser.parseCentsOrZero("100"))
        assertEquals(1L, MoneyParser.parseCentsOrZero("0.005"))
        assertEquals(0L, MoneyParser.parseCentsOrZero("0.004"))
        assertEquals(150_050L, MoneyParser.parseCentsOrZero("$1500.50"))
        assertEquals(0L, MoneyParser.parseCentsOrZero("abc"))
        assertEquals(0L, MoneyParser.parseCentsOrZero(""))
    }

    @Test
    fun sanitizeAmountInputKeepsSingleDecimalPointAndTwoFractionDigits() {
        assertEquals("1500.50", MoneyParser.sanitizeAmountInput("1500.50"))
        assertEquals("12.34", MoneyParser.sanitizeAmountInput("1a2.3b4"))
        assertEquals("1.23", MoneyParser.sanitizeAmountInput("1.2.3"))
        assertEquals("1.23", MoneyParser.sanitizeAmountInput("1.2345"))
        assertEquals("123", MoneyParser.sanitizeAmountInput("0123"))
        assertEquals("0.5", MoneyParser.sanitizeAmountInput("0.5"))
        assertEquals("0", MoneyParser.sanitizeAmountInput("0"))
        assertEquals("", MoneyParser.sanitizeAmountInput("abc"))
    }

    @Test
    fun formatPlainCentsUsesTwoFractionDigits() {
        assertEquals("12.50", MoneyParser.formatPlainCents(1_250))
        assertEquals("12.00", MoneyParser.formatPlainCents(1_200))
        assertEquals("0.01", MoneyParser.formatPlainCents(1))
        assertEquals("1.00", MoneyParser.formatPlainCents(100))
        assertEquals("0.00", MoneyParser.formatPlainCents(0))
        assertEquals("-12.50", MoneyParser.formatPlainCents(-1_250))
        assertEquals("-92233720368547758.08", MoneyParser.formatPlainCents(Long.MIN_VALUE))
    }

    @Test
    fun moneyModuleDoesNotUseBinaryFloatingPointForMoneyMath() {
        val source = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        assertFalse(source.contains("Double"))
        assertFalse(source.contains("Float"))
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
