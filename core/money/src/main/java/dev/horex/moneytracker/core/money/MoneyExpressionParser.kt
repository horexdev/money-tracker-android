package dev.horex.moneytracker.core.money

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class MoneyExpressionResult(
    val ok: Boolean,
    val value: BigDecimal? = null,
)

object MoneyExpressionParser {
    fun looksLikeExpression(input: String): Boolean {
        return input.any { it in ExpressionOperatorChars }
    }

    fun evaluate(input: String): MoneyExpressionResult {
        if (input.length > MAX_EXPRESSION_LENGTH) {
            return MoneyExpressionResult(ok = false)
        }

        val source = input
            .replace('\u00d7', '*')
            .replace('\u00f7', '/')
            .replace('\u2212', '-')
            .replace(',', '.')
            .filterNot(Char::isWhitespace)

        if (source.isEmpty()) {
            return MoneyExpressionResult(ok = false)
        }

        val parser = Parser(source)
        val result = parser.parseAdd()
        if (result == null || !parser.isAtEnd()) {
            return MoneyExpressionResult(ok = false)
        }
        if (result.abs() > MAX_ABS_VALUE) {
            return MoneyExpressionResult(ok = false)
        }
        return MoneyExpressionResult(ok = true, value = result)
    }

    fun evaluateToCents(input: String): Long? {
        val result = evaluate(input)
        return result.value?.takeIf { result.ok }?.let(MoneyMath::decimalToCents)
    }

    fun formatForInput(value: BigDecimal): String {
        return value
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}

private class Parser(private val source: String) {
    private var position = 0

    fun isAtEnd(): Boolean = position == source.length

    fun parseAdd(): BigDecimal? {
        val left = parseMul() ?: return null
        var accumulator = left.value
        while (peek() == '+' || peek() == '-') {
            val operator = eat()
            val right = parseMul() ?: return null
            val delta = if (right.lonePercent) {
                accumulator.multiply(right.value, ExpressionMathContext)
            } else {
                right.value
            }
            accumulator = if (operator == '+') {
                accumulator.add(delta, ExpressionMathContext)
            } else {
                accumulator.subtract(delta, ExpressionMathContext)
            }
        }
        return accumulator
    }

    private fun parseMul(): Term? {
        val left = parseUnary() ?: return null
        if (peek() != '*' && peek() != '/') {
            return left
        }

        var value = left.value
        while (peek() == '*' || peek() == '/') {
            val operator = eat()
            val right = parseUnary() ?: return null
            value = if (operator == '*') {
                value.multiply(right.value, ExpressionMathContext)
            } else {
                if (right.value.compareTo(BigDecimal.ZERO) == 0) {
                    return null
                }
                value.divide(right.value, ExpressionMathContext)
            }
        }
        return Term(value = value, lonePercent = false)
    }

    private fun parseUnary(): Term? {
        if (peek() == '-') {
            eat()
            val term = parseUnary() ?: return null
            return Term(value = term.value.negate(ExpressionMathContext), lonePercent = term.lonePercent)
        }
        if (peek() == '+') {
            eat()
            return parseUnary()
        }
        return parsePostfix()
    }

    private fun parsePostfix(): Term? {
        val value = parsePrimary() ?: return null
        if (peek() == '%') {
            eat()
            return Term(value = value.movePointLeft(2), lonePercent = true)
        }
        return Term(value = value, lonePercent = false)
    }

    private fun parsePrimary(): BigDecimal? {
        if (peek() == '(') {
            eat()
            val value = parseAdd() ?: return null
            if (peek() != ')') {
                return null
            }
            eat()
            return value
        }
        return parseNumber()
    }

    private fun parseNumber(): BigDecimal? {
        val start = position
        var dotSeen = false
        var digitSeen = false

        while (position < source.length) {
            val char = source[position]
            if (char in '0'..'9') {
                digitSeen = true
                position++
                continue
            }
            if (char == '.') {
                if (dotSeen) {
                    return null
                }
                dotSeen = true
                position++
                continue
            }
            break
        }

        if (!digitSeen) {
            return null
        }

        return try {
            BigDecimal(source.substring(start, position), ExpressionMathContext)
        } catch (error: NumberFormatException) {
            null
        }
    }

    private fun peek(): Char = source.getOrNull(position) ?: '\u0000'

    private fun eat(): Char = source.getOrNull(position++) ?: '\u0000'
}

private data class Term(
    val value: BigDecimal,
    val lonePercent: Boolean,
)

private const val MAX_EXPRESSION_LENGTH = 64

private val MAX_ABS_VALUE = BigDecimal("10000000000")

private val ExpressionMathContext = MathContext(34, RoundingMode.HALF_UP)

private val ExpressionOperatorChars = setOf(
    '+',
    '-',
    '*',
    '/',
    '\u00d7',
    '\u00f7',
    '\u2212',
    '(',
    ')',
    '%',
)
