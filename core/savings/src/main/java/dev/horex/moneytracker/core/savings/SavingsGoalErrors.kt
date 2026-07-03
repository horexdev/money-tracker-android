package dev.horex.moneytracker.core.savings

sealed class SavingsGoalException(message: String) : RuntimeException(message)

class SavingsGoalNotFoundException : SavingsGoalException("Savings goal was not found in the target profile")

class SavingsGoalNameEmptyException : SavingsGoalException("Savings goal name must not be empty")

class InvalidSavingsGoalAmountException : SavingsGoalException("Savings goal amount must be positive")

class InvalidSavingsGoalCurrencyException : SavingsGoalException("Savings goal currency must be a 3-letter ISO code")

class InvalidSavingsGoalDeadlineException : SavingsGoalException("Savings goal deadline must use YYYY-MM-DD format")

class SavingsGoalAccountNotFoundException : SavingsGoalException("Savings goal linked account was not found")

class SavingsGoalCategoryNotFoundException : SavingsGoalException("Savings goal category was not found")

class SavingsGoalInsufficientFundsException : SavingsGoalException("Savings goal current amount is lower than requested withdrawal")

class InvalidSavingsGoalHistoryTypeException : SavingsGoalException("Savings goal history type is invalid")

class SavingsGoalLinkConflictException : SavingsGoalException("Savings goal account link cannot be set and cleared at once")
