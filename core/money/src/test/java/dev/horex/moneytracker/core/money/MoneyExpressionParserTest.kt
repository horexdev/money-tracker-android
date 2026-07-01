package dev.horex.moneytracker.core.money

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyExpressionParserTest {
    @Test
    fun looksLikeExpressionDetectsOperators() {
        mapOf(
            "1+2" to true,
            "10*5" to true,
            "100-20" to true,
            "10\u00d75" to true,
            "10\u00f75" to true,
            "200%" to true,
            "(1+2)" to true,
            "100" to false,
            "1500.50" to false,
            "" to false,
        ).forEach { (input, expected) ->
            assertEquals(expected, MoneyExpressionParser.looksLikeExpression(input))
        }
    }

    @Test
    fun evaluateHandlesArithmeticPrecedenceAndInputNormalization() {
        assertExpression("1+2", "3")
        assertExpression("2*3", "6")
        assertExpression("1.5+2.5", "4.0")
        assertExpression("2+3*4", "14")
        assertExpression("(2+3)*4", "20")
        assertExpression("-5+10", "5")
        assertExpression("2\u00d73\u00f76", "1")
        assertExpression("10\u22125", "5")
        assertExpression("1,5+0,5", "2.0")
        assertExpression(" 1 + 2 ", "3")
    }

    @Test
    fun evaluateHandlesPercentSyntax() {
        assertExpression("10%", "0.10")
        assertExpression("200+10%", "220.00")
        assertExpression("200-10%", "180.00")
    }

    @Test
    fun evaluateRejectsInvalidExpressions() {
        listOf(
            "",
            "1+".repeat(40) + "1",
            "1+",
            "1*2*",
            "(1+2",
            "+",
            "1.2.3",
            "1/0",
            "99999999999",
        ).forEach { input ->
            assertFalse("Expected invalid expression for $input", MoneyExpressionParser.evaluate(input).ok)
        }
    }

    @Test
    fun evaluateToCentsRoundsHalfUp() {
        assertEquals(124L, MoneyExpressionParser.evaluateToCents("1.235"))
        assertEquals(123L, MoneyExpressionParser.evaluateToCents("1.234"))
        assertEquals(22_000L, MoneyExpressionParser.evaluateToCents("200+10%"))
        assertEquals(null, MoneyExpressionParser.evaluateToCents("1/0"))
    }

    @Test
    fun formatForInputRoundsAndDropsTrailingZeros() {
        assertEquals("1.23", MoneyExpressionParser.formatForInput(BigDecimal("1.234")))
        assertEquals("1.24", MoneyExpressionParser.formatForInput(BigDecimal("1.236")))
        assertEquals("1.5", MoneyExpressionParser.formatForInput(BigDecimal("1.50")))
        assertEquals("2", MoneyExpressionParser.formatForInput(BigDecimal("2.00")))
    }

    private fun assertExpression(input: String, expected: String) {
        val result = MoneyExpressionParser.evaluate(input)
        assertTrue("Expected expression to be valid: $input", result.ok)
        assertEquals(0, BigDecimal(expected).compareTo(checkNotNull(result.value)))
    }
}
