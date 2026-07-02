package dev.horex.moneytracker.core.stats

data class CategoryStat(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val type: StatsTransactionType,
    val totalCents: Long,
    val transactionCount: Long,
    val currencyCode: String,
)

data class StatsSnapshot(
    val profileId: Long,
    val period: String,
    val range: StatsRange,
    val items: List<CategoryStat>,
)

data class StatsRange(
    val fromEpochMillisInclusive: Long,
    val toEpochMillisExclusive: Long,
)

enum class StatsPeriod(val storageValue: String) {
    Today("today"),
    Week("week"),
    Month("month"),
    LastMonth("lastmonth"),
    ;
}

enum class StatsTransactionType(val storageValue: String) {
    Expense("expense"),
    Income("income"),
    ;

    companion object {
        fun fromStorageValue(value: String): StatsTransactionType {
            return entries.first { it.storageValue == value }
        }
    }
}

data class StatsQuery(
    val period: StatsPeriod = StatsPeriod.Month,
    val accountId: Long? = null,
    val customRange: StatsRange? = null,
)
