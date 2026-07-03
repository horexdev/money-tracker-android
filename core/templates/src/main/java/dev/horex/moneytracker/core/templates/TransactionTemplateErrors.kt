package dev.horex.moneytracker.core.templates

sealed class TransactionTemplateException(message: String) : RuntimeException(message)

class TransactionTemplateNotFoundException : TransactionTemplateException(
    "Transaction template was not found in the target profile",
)

class InvalidTransactionTemplateAmountException : TransactionTemplateException(
    "Transaction template amount must be positive",
)

class InvalidTransactionTemplateSortOrderException : TransactionTemplateException(
    "Transaction template sort order must not be negative",
)

class InvalidTransactionTemplateReorderException : TransactionTemplateException(
    "Transaction template reorder input must contain every target profile template exactly once",
)

class TransactionTemplateAccountNotFoundException : TransactionTemplateException(
    "Transaction template account was not found in the target profile",
)

class TransactionTemplateCategoryNotFoundException : TransactionTemplateException(
    "Transaction template category was not found in the target profile",
)

class TransactionTemplateCategoryTypeException : TransactionTemplateException(
    "Transaction template category cannot be used for this transaction type",
)
