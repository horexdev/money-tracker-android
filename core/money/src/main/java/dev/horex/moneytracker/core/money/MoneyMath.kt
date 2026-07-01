package dev.horex.moneytracker.core.money

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyMath {
    const val RATE_SCALE_E8: Long = 100_000_000L

    fun addCents(left: Long, right: Long): Long {
        return try {
            Math.addExact(left, right)
        } catch (error: ArithmeticException) {
            throw MoneyOverflowException()
        }
    }

    fun subtractCents(left: Long, right: Long): Long {
        return try {
            Math.subtractExact(left, right)
        } catch (error: ArithmeticException) {
            throw MoneyOverflowException()
        }
    }

    fun sumCents(values: Iterable<Long>): Long {
        return values.fold(0L, ::addCents)
    }

    fun convertCents(amountCents: Long, rateE8: Long): Long {
        if (rateE8 <= 0) {
            throw InvalidMoneyAmountException()
        }
        return try {
            BigDecimal.valueOf(amountCents)
                .multiply(BigDecimal.valueOf(rateE8))
                .divide(BigDecimal.valueOf(RATE_SCALE_E8), 0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (error: ArithmeticException) {
            throw MoneyOverflowException()
        }
    }

    fun decimalToCents(value: BigDecimal): Long {
        return try {
            value
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (error: ArithmeticException) {
            throw MoneyOverflowException()
        }
    }
}
