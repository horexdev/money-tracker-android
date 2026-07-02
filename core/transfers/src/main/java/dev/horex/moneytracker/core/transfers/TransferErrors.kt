package dev.horex.moneytracker.core.transfers

sealed class TransferException(message: String) : RuntimeException(message)

class TransferNotFoundException : TransferException("Transfer was not found in the target profile")

class TransferSameAccountException : TransferException("Transfer source and destination accounts must be different")

class InvalidTransferAmountException : TransferException("Transfer amount must be positive")

class TransferAccountNotFoundException : TransferException("Transfer account was not found in the target profile")

class TransferCategoryNotFoundException : TransferException("Transfer category was not found in the target profile")

class TransferExchangeRateNotFoundException : TransferException("Transfer exchange rate was not found")

class TransferAmountOverflowException : TransferException("Transfer converted amount is out of range")

class TransferLinkedTransactionNotFoundException : TransferException("Transfer linked transaction was not found")
