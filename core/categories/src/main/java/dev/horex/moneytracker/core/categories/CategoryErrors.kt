package dev.horex.moneytracker.core.categories

open class CategoryException(message: String) : RuntimeException(message)

class CategoryNotFoundException : CategoryException("Category was not found in the target profile")

class CategoryNameEmptyException : CategoryException("Category name must not be empty")

class CategoryProtectedException : CategoryException("Protected categories cannot be modified")
