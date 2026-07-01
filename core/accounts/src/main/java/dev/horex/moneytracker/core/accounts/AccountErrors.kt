package dev.horex.moneytracker.core.accounts

sealed class AccountException(message: String) : RuntimeException(message)

class AccountNotFoundException : AccountException("Account not found")

class AccountNameEmptyException : AccountException("Account name cannot be blank")

class InvalidAccountCurrencyException : AccountException("Account currency must be a 3-letter ISO code")

class CurrencyImmutableException : AccountException("Account currency cannot be changed after creation")

class CannotDeleteLastAccountException : AccountException("Cannot delete the only account")

class MustSetNewDefaultAccountException : AccountException("Must set a new default account before deleting this account")

class AccountHasTransactionsException : AccountException("Account has transactions")

class AccountHasTransfersException : AccountException("Account has transfers")

class AccountHasRecurringTransactionsException : AccountException("Account has recurring transactions")

class AccountHasTemplatesException : AccountException("Account has transaction templates")
