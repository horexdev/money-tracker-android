package dev.horex.moneytracker.core.money

sealed class MoneyException(message: String) : RuntimeException(message)

class InvalidMoneyAmountException : MoneyException("Money amount must be a valid non-zero cent amount")

class InvalidMoneyExpressionException : MoneyException("Money expression is invalid")

class MoneyOverflowException : MoneyException("Money operation overflowed")
