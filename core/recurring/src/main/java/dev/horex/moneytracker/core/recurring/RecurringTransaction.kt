package dev.horex.moneytracker.core.recurring

import dev.horex.moneytracker.core.transactions.TransactionType
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

const val DEFAULT_RECURRING_DUE_LIMIT = 100

data class RecurringTransaction(
    val id: Long,
    val profileId: Long,
    val accountId: Long,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val type: TransactionType,
    val amountCents: Long,
    val currencyCode: String,
    val note: String,
    val frequency: RecurringFrequency,
    val nextRunAtEpochMillis: Long,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

enum class RecurringFrequency(val storageValue: String) {
    Daily("daily"),
    Weekly("weekly"),
    Monthly("monthly"),
    Yearly("yearly"),
    ;

    fun nextRunAfter(
        afterEpochMillis: Long,
        zoneId: ZoneId = ZoneOffset.UTC,
    ): Long {
        val after = Instant.ofEpochMilli(afterEpochMillis)
            .atZone(zoneId)
            .toLocalDateTime()
        val next = when (this) {
            Daily -> after.plusDays(1)
            Weekly -> after.plusDays(7)
            Monthly -> after.addDateLikeSource(months = 1)
            Yearly -> after.addDateLikeSource(years = 1)
        }
        return next.atZone(zoneId).toInstant().toEpochMilli()
    }

    companion object {
        fun fromStorageValue(value: String): RecurringFrequency {
            return entries.firstOrNull { it.storageValue == value } ?: throw InvalidRecurringFrequencyException()
        }
    }
}

data class CreateRecurringTransactionInput(
    val type: TransactionType,
    val amountCents: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String = "",
    val frequency: RecurringFrequency,
    val nextRunAtEpochMillis: Long? = null,
)

data class UpdateRecurringTransactionInput(
    val type: TransactionType? = null,
    val amountCents: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val note: String? = null,
    val frequency: RecurringFrequency? = null,
    val nextRunAtEpochMillis: Long? = null,
)

data class ProcessRecurringDueResult(
    val processedCount: Int,
    val skippedCount: Int,
)

private fun LocalDateTime.addDateLikeSource(
    years: Long = 0,
    months: Long = 0,
): LocalDateTime {
    val targetMonth = YearMonth.from(this)
        .plusYears(years)
        .plusMonths(months)
    val targetDate = if (dayOfMonth <= targetMonth.lengthOfMonth()) {
        targetMonth.atDay(dayOfMonth)
    } else {
        targetMonth.atEndOfMonth().plusDays((dayOfMonth - targetMonth.lengthOfMonth()).toLong())
    }
    return LocalDateTime.of(targetDate, toLocalTime())
}
