package dev.horex.moneytracker.core.currency

sealed class CurrencyException(message: String) : RuntimeException(message)

class InvalidCurrencyCodeException : CurrencyException("Currency code must be a supported ISO 4217 code")

class InvalidExchangeRateDateException : CurrencyException("Exchange rate date must use ISO yyyy-MM-dd format")

class InvalidExchangeRateException : CurrencyException("Exchange rate must be greater than zero")

class ExchangeRateNotFoundException : CurrencyException("Exchange rate not found")

class ExchangeRateOverrideNotFoundException : CurrencyException("Exchange rate override not found")
