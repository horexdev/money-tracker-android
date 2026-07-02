package dev.horex.moneytracker.core.transactions

sealed class TransactionException(message: String) : RuntimeException(message)

class TransactionNotFoundException : TransactionException("Transaction was not found in the target profile")

class InvalidTransactionAmountException : TransactionException("Transaction amount must be positive")

class InvalidAdjustmentDeltaException : TransactionException("Adjustment delta must not be zero or overflow")

class InvalidTransactionTypeException : TransactionException("Transaction type is invalid")

class InvalidTransactionDateRangeException : TransactionException("Transaction date range is invalid")

class TransactionAccountNotFoundException : TransactionException("Transaction account was not found in the target profile")

class TransactionCategoryNotFoundException : TransactionException("Transaction category was not found in the target profile")

class TransactionCategoryTypeException : TransactionException("Transaction category cannot be used for this transaction type")

class AdjustmentTransactionImmutableException : TransactionException("Adjustment transactions cannot be edited or deleted here")

class TransactionLinkedToTransferException : TransactionException("Linked transfer transactions cannot be edited or deleted directly")
