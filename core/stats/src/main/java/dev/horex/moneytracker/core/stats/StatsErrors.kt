package dev.horex.moneytracker.core.stats

open class StatsException(message: String) : RuntimeException(message)

class InvalidStatsDateRangeException : StatsException("Invalid stats date range")

class StatsAccountNotFoundException : StatsException("Stats account not found")
