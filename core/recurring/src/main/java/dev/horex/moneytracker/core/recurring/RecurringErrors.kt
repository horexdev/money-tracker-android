package dev.horex.moneytracker.core.recurring

sealed class RecurringException(message: String) : RuntimeException(message)

class RecurringTransactionNotFoundException : RecurringException(
    "Recurring transaction was not found in the target profile",
)

class InvalidRecurringAmountException : RecurringException("Recurring amount must be positive")

class InvalidRecurringFrequencyException : RecurringException("Recurring frequency is invalid")

class InvalidRecurringNextRunException : RecurringException("Recurring next run time is invalid")

class RecurringAccountNotFoundException : RecurringException(
    "Recurring account was not found in the target profile",
)

class RecurringCategoryNotFoundException : RecurringException(
    "Recurring category was not found in the target profile",
)

class RecurringCategoryTypeException : RecurringException(
    "Recurring category cannot be used for this transaction type",
)
