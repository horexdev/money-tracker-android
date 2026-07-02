package dev.horex.moneytracker.core.transactions

data class MoneyTransaction(
    val id: Long,
    val profileId: Long,
    val type: TransactionType,
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
    val isAdjustment: Boolean,
)

enum class TransactionType(val storageValue: String) {
    Expense("expense"),
    Income("income"),
    ;

    companion object {
        fun fromStorageValue(value: String): TransactionType {
            return entries.firstOrNull { it.storageValue == value } ?: throw InvalidTransactionTypeException()
        }
    }
}

data class CreateTransactionInput(
    val type: TransactionType,
    val amountCents: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String = "",
    val createdAtEpochMillis: Long? = null,
)

data class BalanceAdjustmentInput(
    val accountId: Long,
    val deltaCents: Long,
    val note: String = "",
    val createdAtEpochMillis: Long? = null,
)

data class UpdateTransactionInput(
    val amountCents: Long,
    val categoryId: Long,
    val note: String = "",
    val createdAtEpochMillis: Long? = null,
)

data class TransactionQuery(
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null,
    val searchText: String? = null,
    val page: Int = 1,
    val pageSize: Int = DEFAULT_TRANSACTION_PAGE_SIZE,
)

data class TransactionPage(
    val transactions: List<MoneyTransaction>,
    val totalPages: Int,
    val currentPage: Int,
)

const val DEFAULT_TRANSACTION_PAGE_SIZE = 20
const val MAX_TRANSACTION_PAGE_SIZE = 100
