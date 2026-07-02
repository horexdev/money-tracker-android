package dev.horex.moneytracker.core.money

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object MoneyParser {
    fun parsePositiveCents(input: String): Long {
        val value = input.trim()
        if (value.isEmpty()) {
            throw InvalidMoneyAmountException()
        }

        val normalized = value.replace(',', '.')
        val parts = normalized.split('.')
        if (parts.size > 2 || parts[0].isEmpty()) {
            throw InvalidMoneyAmountException()
        }

        val whole = parts[0].parseDigitsAsLongOrThrow()
        if (whole < 0) {
            throw InvalidMoneyAmountException()
        }

        val fraction = if (parts.size == 2) {
            parts[1].parseFractionOrThrow()
        } else {
            0
        }

        val cents = try {
            Math.addExact(Math.multiplyExact(whole, CENTS_IN_UNIT), fraction)
        } catch (error: ArithmeticException) {
            throw MoneyOverflowException()
        }
        if (cents <= 0) {
            throw InvalidMoneyAmountException()
        }
        return cents
    }

    fun parseCentsOrZero(input: String): Long {
        val cleaned = input.replace(Regex("[^0-9.]"), "")
        if (cleaned.isEmpty() || cleaned == ".") {
            return 0
        }
        return try {
            BigDecimal(cleaned)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (error: RuntimeException) {
            0
        }
    }

    fun parseSignedCentsOrZero(input: String): Long {
        val trimmed = input.trimStart()
        val isNegative = trimmed.startsWith("-")
        val unsignedInput = if (isNegative) trimmed.drop(1) else trimmed
        val unsigned = unsignedInput.replace(Regex("[^0-9.]"), "")
        val cleaned = if (isNegative) "-$unsigned" else unsigned
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == "-.") {
            return 0
        }
        return try {
            BigDecimal(cleaned)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (error: RuntimeException) {
            0
        }
    }

    fun sanitizeAmountInput(input: String): String {
        var cleaned = input.replace(Regex("[^0-9.]"), "")
        val dotIndex = cleaned.indexOf('.')
        if (dotIndex != -1) {
            cleaned = cleaned.substring(0, dotIndex + 1) +
                cleaned.substring(dotIndex + 1).replace(".", "")
        }
        if (dotIndex != -1 && cleaned.length - dotIndex > 3) {
            cleaned = cleaned.substring(0, dotIndex + 3)
        }
        if (cleaned.length > 1 && cleaned[0] == '0' && cleaned[1] != '.') {
            cleaned = cleaned.substring(1)
        }
        return cleaned
    }

    fun sanitizeSignedAmountInput(input: String): String {
        val trimmed = input.trimStart()
        val isNegative = trimmed.startsWith("-")
        val unsignedInput = if (isNegative) trimmed.drop(1) else trimmed
        val unsigned = sanitizeAmountInput(unsignedInput)
        return if (isNegative) {
            if (unsigned.isEmpty()) "-" else "-$unsigned"
        } else {
            unsigned
        }
    }

    fun formatPlainCents(cents: Long): String {
        val amount = BigInteger.valueOf(cents)
        val sign = if (amount.signum() < 0) "-" else ""
        val absolute = amount.abs()
        val whole = absolute / BigInteger.valueOf(CENTS_IN_UNIT)
        val fraction = (absolute % BigInteger.valueOf(CENTS_IN_UNIT)).toString().padStart(2, '0')
        return "$sign$whole.$fraction"
    }

    private fun String.parseDigitsAsLongOrThrow(): Long {
        if (isEmpty() || !all(Char::isDigit)) {
            throw InvalidMoneyAmountException()
        }
        return try {
            toLong()
        } catch (error: NumberFormatException) {
            throw MoneyOverflowException()
        }
    }

    private fun String.parseFractionOrThrow(): Long {
        if (isEmpty() || length > 2 || !all(Char::isDigit)) {
            throw InvalidMoneyAmountException()
        }
        return (if (length == 1) this + "0" else this).toLong()
    }
}

private const val CENTS_IN_UNIT = 100L
