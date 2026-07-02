package dev.horex.moneytracker.core.stats

import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.dao.CategoryStatsRow
import java.time.LocalDate
import java.time.ZoneId

class RoomStatsRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : StatsRepository {
    private val accountDao = database.accountDao()
    private val transactionDao = database.transactionDao()

    override suspend fun getStats(profileId: Long, query: StatsQuery): StatsSnapshot {
        val normalized = query.normalized()
        val accountId = normalized.accountId
        if (accountId != null && accountDao.getById(profileId, accountId) == null) {
            throw StatsAccountNotFoundException()
        }

        val range = normalized.customRange ?: normalized.period.toRange()
        val period = if (normalized.customRange != null) CUSTOM_PERIOD else normalized.period.storageValue
        val items = transactionDao.getStatsByCategory(
            profileId = profileId,
            accountId = accountId,
            fromEpochMillisInclusive = range.fromEpochMillisInclusive,
            toEpochMillisExclusive = range.toEpochMillisExclusive,
        ).map(CategoryStatsRow::toCategoryStat)

        return StatsSnapshot(
            profileId = profileId,
            period = period,
            range = range,
            items = items,
        )
    }

    private fun StatsPeriod.toRange(): StatsRange {
        val today = clock().toLocalDate()
        return when (this) {
            StatsPeriod.Today -> today.rangeUntil(today.plusDays(1))
            StatsPeriod.Week -> {
                val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                monday.rangeUntil(monday.plusDays(7))
            }
            StatsPeriod.Month -> today.withDayOfMonth(1).let { start ->
                start.rangeUntil(start.plusMonths(1))
            }
            StatsPeriod.LastMonth -> today.withDayOfMonth(1).let { currentMonth ->
                currentMonth.minusMonths(1).rangeUntil(currentMonth)
            }
        }
    }

    private fun Long.toLocalDate(): LocalDate {
        return java.time.Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    }

    private fun LocalDate.rangeUntil(endExclusive: LocalDate): StatsRange {
        return StatsRange(
            fromEpochMillisInclusive = atStartOfDay(zoneId).toInstant().toEpochMilli(),
            toEpochMillisExclusive = endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }
}

private const val CUSTOM_PERIOD = "custom"

private fun StatsQuery.normalized(): StatsQuery {
    val custom = customRange
    if (custom != null && custom.fromEpochMillisInclusive >= custom.toEpochMillisExclusive) {
        throw InvalidStatsDateRangeException()
    }
    return this
}

private fun CategoryStatsRow.toCategoryStat(): CategoryStat {
    return CategoryStat(
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        type = StatsTransactionType.fromStorageValue(type),
        totalCents = totalCents,
        transactionCount = transactionCount,
        currencyCode = currencyCode,
    )
}
