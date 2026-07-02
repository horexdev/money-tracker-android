package dev.horex.moneytracker.core.transfers

data class Transfer(
    val id: Long,
    val profileId: Long,
    val fromAccountId: Long,
    val fromAccountName: String,
    val toAccountId: Long,
    val toAccountName: String,
    val amountCents: Long,
    val fromCurrencyCode: String,
    val toCurrencyCode: String,
    val exchangeRateE8: Long,
    val note: String,
    val fromTransactionId: Long?,
    val toTransactionId: Long?,
    val createdAtEpochMillis: Long,
)

data class CreateTransferInput(
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountCents: Long,
    val exchangeRateE8: Long? = null,
    val note: String = "",
    val createdAtEpochMillis: Long? = null,
)

data class TransferQuery(
    val accountId: Long? = null,
    val page: Int = 1,
    val pageSize: Int = DEFAULT_TRANSFER_PAGE_SIZE,
)

data class TransferPage(
    val transfers: List<Transfer>,
    val totalPages: Int,
    val currentPage: Int,
)

const val DEFAULT_TRANSFER_PAGE_SIZE = 50
const val MAX_TRANSFER_PAGE_SIZE = 200
