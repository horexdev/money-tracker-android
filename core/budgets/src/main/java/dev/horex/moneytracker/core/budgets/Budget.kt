package dev.horex.moneytracker.core.budgets

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val DEFAULT_BUDGET_NOTIFY_AT_PERCENT = 80
val BudgetAlertThresholds = listOf(50, 75, 95, 100)

data class Budget(
    val id: Long,
    val profileId: Long,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val limitCents: Long,
    val spentCents: Long,
    val period: BudgetPeriod,
    val currencyCode: String,
    val notifyAtPercent: Int,
    val notificationsEnabled: Boolean,
    val lastNotifiedPercent: Int,
    val lastNotifiedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val usagePercent: Double
        get() = if (limitCents <= 0L) 0.0 else spentCents.toDouble() / limitCents.toDouble() * 100.0

    val isOverLimit: Boolean
        get() = spentCents >= limitCents

    fun crossedAlertThresholds(periodStartEpochMillis: Long): List<Int> {
        if (!notificationsEnabled) {
            return emptyList()
        }
        val alreadyNotified = if (
            lastNotifiedAtEpochMillis != null &&
            lastNotifiedAtEpochMillis < periodStartEpochMillis
        ) {
            0
        } else {
            lastNotifiedPercent
        }
        return BudgetAlertThresholds.filter { threshold ->
            usagePercent >= threshold && alreadyNotified < threshold
        }
    }

    fun nextAlertThreshold(periodStartEpochMillis: Long): Int {
        return crossedAlertThresholds(periodStartEpochMillis).firstOrNull() ?: 0
    }
}

data class BudgetTransaction(
    val id: Long,
    val profileId: Long,
    val amountCents: Long,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val accountId: Long,
    val accountName: String,
    val note: String,
    val currencyCode: String,
    val snapshotDate: String,
    val createdAtEpochMillis: Long,
)

enum class BudgetPeriod(val storageValue: String) {
    Weekly("weekly"),
    Monthly("monthly"),
    ;

    companion object {
        fun fromStorageValue(value: String): BudgetPeriod {
            return entries.firstOrNull { it.storageValue == value } ?: throw InvalidBudgetPeriodException()
        }
    }
}

data class BudgetPeriodRange(
    val fromEpochMillisInclusive: Long,
    val toEpochMillisExclusive: Long,
)

data class CreateBudgetInput(
    val categoryId: Long,
    val limitCents: Long,
    val period: BudgetPeriod,
    val currencyCode: String,
    val notifyAtPercent: Int = DEFAULT_BUDGET_NOTIFY_AT_PERCENT,
    val notificationsEnabled: Boolean = true,
)

data class UpdateBudgetInput(
    val limitCents: Long? = null,
    val period: BudgetPeriod? = null,
    val notifyAtPercent: Int? = null,
    val notificationsEnabled: Boolean? = null,
)

fun BudgetPeriod.currentRange(
    nowEpochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): BudgetPeriodRange {
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
    return when (this) {
        BudgetPeriod.Weekly -> {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            monday.rangeUntil(monday.plusDays(7), zoneId)
        }
        BudgetPeriod.Monthly -> today.withDayOfMonth(1).let { monthStart ->
            monthStart.rangeUntil(monthStart.plusMonths(1), zoneId)
        }
    }
}

private fun LocalDate.rangeUntil(endExclusive: LocalDate, zoneId: ZoneId): BudgetPeriodRange {
    return BudgetPeriodRange(
        fromEpochMillisInclusive = atStartOfDay(zoneId).toInstant().toEpochMilli(),
        toEpochMillisExclusive = endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
    )
}
