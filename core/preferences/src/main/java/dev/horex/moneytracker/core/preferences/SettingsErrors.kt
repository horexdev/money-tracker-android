package dev.horex.moneytracker.core.preferences

sealed class SettingsException(message: String) : RuntimeException(message)

class SettingsProfileNotFoundException : SettingsException("Settings profile not found")

class InvalidSettingsLanguageException : SettingsException("Unsupported settings language code")

class InvalidSettingsCurrencyException : SettingsException("Settings currency must be a supported ISO code")

class TooManySettingsDisplayCurrenciesException : SettingsException("Maximum 3 display currencies allowed")
