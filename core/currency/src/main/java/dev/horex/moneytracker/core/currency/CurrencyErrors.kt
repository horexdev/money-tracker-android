package dev.horex.moneytracker.core.currency

sealed class CurrencyException(message: String) : RuntimeException(message)

class InvalidCurrencyCodeException : CurrencyException("Currency code must be a supported ISO 4217 code")

class InvalidExchangeRateDateException : CurrencyException("Exchange rate date must use ISO yyyy-MM-dd format")

class InvalidExchangeRateException : CurrencyException("Exchange rate must be greater than zero")

class ExchangeRateNotFoundException : CurrencyException("Exchange rate not found")

class ExchangeRateOverrideAlreadyExistsException(
    val existing: ExchangeRateOverride,
) : CurrencyException("Exchange rate override already exists")

class ExchangeRateOverrideNotFoundException : CurrencyException("Exchange rate override not found")

class OnlineExchangeRateUpdateException(
    cause: Throwable? = null,
) : CurrencyException("Online exchange rate update failed") {
    init {
        if (cause != null) {
            initCause(cause)
        }
    }
}

class OnlineExchangeRateResponseException : CurrencyException("Online exchange rate response is invalid")
