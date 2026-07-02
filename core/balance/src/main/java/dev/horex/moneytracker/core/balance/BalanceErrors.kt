package dev.horex.moneytracker.core.balance

sealed class BalanceException(message: String) : RuntimeException(message)

class BalanceAccountNotFoundException : BalanceException("Balance account was not found.")

class InvalidBalanceCurrencyException : BalanceException("Balance currency code is invalid.")

class BalanceExchangeRateNotFoundException : BalanceException("Balance exchange rate was not found.")

class BalanceAmountOverflowException : BalanceException("Balance amount overflowed.")
