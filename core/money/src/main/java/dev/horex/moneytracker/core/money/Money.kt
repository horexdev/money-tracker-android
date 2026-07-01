package dev.horex.moneytracker.core.money

data class MoneyAmount(
    val cents: Long,
    val currencyCode: String,
) {
    init {
        require(currencyCode.matches(CurrencyCodePattern)) {
            "currencyCode must be a 3-letter ISO code"
        }
    }

    operator fun plus(other: MoneyAmount): MoneyAmount {
        requireSameCurrency(other)
        return copy(cents = MoneyMath.addCents(cents, other.cents))
    }

    operator fun minus(other: MoneyAmount): MoneyAmount {
        requireSameCurrency(other)
        return copy(cents = MoneyMath.subtractCents(cents, other.cents))
    }

    private fun requireSameCurrency(other: MoneyAmount) {
        require(currencyCode == other.currencyCode) {
            "Money amounts must use the same currency"
        }
    }
}

private val CurrencyCodePattern = Regex("[A-Z]{3}")
