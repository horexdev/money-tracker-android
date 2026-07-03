package dev.horex.moneytracker.core.budgets

open class BudgetException(message: String) : RuntimeException(message)

class BudgetNotFoundException : BudgetException("Budget was not found in the target profile")

class BudgetAlreadyExistsException : BudgetException("Budget already exists for this category and period")

class InvalidBudgetAmountException : BudgetException("Budget amount must be positive")

class InvalidBudgetPeriodException : BudgetException("Budget period is invalid")

class InvalidBudgetCurrencyException : BudgetException("Budget currency is invalid")

class InvalidBudgetNotifyPercentException : BudgetException("Budget notify percent must be positive")

class BudgetCategoryNotFoundException : BudgetException("Budget category was not found in the target profile")

class BudgetCategoryTypeException : BudgetException("Budget category must support expenses")
