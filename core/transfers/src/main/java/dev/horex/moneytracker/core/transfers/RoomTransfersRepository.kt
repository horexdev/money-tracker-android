package dev.horex.moneytracker.core.transfers

import androidx.room.withTransaction
import dev.horex.moneytracker.core.currency.CurrencyRatesRepository
import dev.horex.moneytracker.core.currency.ExchangeRateNotFoundException
import dev.horex.moneytracker.core.currency.RATE_SCALE_E8
import dev.horex.moneytracker.core.currency.RoomCurrencyRatesRepository
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.dao.TransferWithAccounts
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.model.TransferEntity
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset

class RoomTransfersRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val currencyRatesRepository: CurrencyRatesRepository = RoomCurrencyRatesRepository(database, clock),
) : TransfersRepository {
    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val transactionDao = database.transactionDao()
    private val transferDao = database.transferDao()

    override suspend fun createTransfer(
        profileId: Long,
        input: CreateTransferInput,
    ): Transfer {
        requireDifferentAccounts(input.fromAccountId, input.toAccountId)
        requirePositiveAmount(input.amountCents)

        val createdAt = input.createdAtEpochMillis ?: clock()
        val snapshotDate = createdAt.toUtcSnapshotDate()

        val transferId = database.withTransaction {
            val fromAccount = requireAccount(profileId, input.fromAccountId)
            val toAccount = requireAccount(profileId, input.toAccountId)
            val transferCategory = requireTransferCategory(profileId)
            val exchangeRateE8 = resolveExchangeRateE8(
                profileId = profileId,
                fromCurrencyCode = fromAccount.currencyCode,
                toCurrencyCode = toAccount.currencyCode,
                snapshotDate = snapshotDate,
                requestedRateE8 = input.exchangeRateE8,
            )
            val toAmountCents = input.amountCents.convertByRate(exchangeRateE8)

            val fromTransactionId = transactionDao.insert(
                TransactionEntity(
                    profileId = profileId,
                    type = TRANSACTION_TYPE_EXPENSE,
                    amountCents = input.amountCents,
                    categoryId = transferCategory.id,
                    accountId = fromAccount.id,
                    note = input.note,
                    currencyCode = fromAccount.currencyCode,
                    snapshotDate = snapshotDate,
                    isAdjustment = false,
                    createdAtEpochMillis = createdAt,
                ),
            )
            val toTransactionId = transactionDao.insert(
                TransactionEntity(
                    profileId = profileId,
                    type = TRANSACTION_TYPE_INCOME,
                    amountCents = toAmountCents,
                    categoryId = transferCategory.id,
                    accountId = toAccount.id,
                    note = input.note,
                    currencyCode = toAccount.currencyCode,
                    snapshotDate = snapshotDate,
                    isAdjustment = false,
                    createdAtEpochMillis = createdAt,
                ),
            )

            transferDao.insert(
                TransferEntity(
                    profileId = profileId,
                    fromAccountId = fromAccount.id,
                    toAccountId = toAccount.id,
                    amountCents = input.amountCents,
                    fromCurrencyCode = fromAccount.currencyCode,
                    toCurrencyCode = toAccount.currencyCode,
                    exchangeRateE8 = exchangeRateE8,
                    note = input.note,
                    fromTransactionId = fromTransactionId,
                    toTransactionId = toTransactionId,
                    createdAtEpochMillis = createdAt,
                ),
            )
        }

        return getTransfer(profileId, transferId)
    }

    override suspend fun getTransfer(profileId: Long, transferId: Long): Transfer {
        return transferDao.getWithAccounts(profileId, transferId)?.toTransfer()
            ?: throw TransferNotFoundException()
    }

    override suspend fun listTransfers(profileId: Long, query: TransferQuery): TransferPage {
        val normalized = query.normalized()
        val total = transferDao.countWithFilters(
            profileId = profileId,
            accountId = normalized.accountId,
        )
        val totalPages = total.totalPages(normalized.pageSize)
        val currentPage = normalized.page.coerceIn(1, totalPages)
        val offset = (currentPage - 1) * normalized.pageSize
        val transfers = transferDao.listWithFilters(
            profileId = profileId,
            accountId = normalized.accountId,
            limit = normalized.pageSize,
            offset = offset,
        ).map(TransferWithAccounts::toTransfer)

        return TransferPage(
            transfers = transfers,
            totalPages = totalPages,
            currentPage = currentPage,
        )
    }

    override suspend fun deleteTransfer(profileId: Long, transferId: Long) {
        database.withTransaction {
            val transfer = transferDao.getById(profileId, transferId) ?: throw TransferNotFoundException()
            if (transferDao.deleteById(profileId, transferId) != 1) {
                throw TransferNotFoundException()
            }
            transfer.fromTransactionId?.let { linkedTransactionId ->
                if (transactionDao.deleteById(profileId, linkedTransactionId) != 1) {
                    throw TransferLinkedTransactionNotFoundException()
                }
            }
            transfer.toTransactionId?.let { linkedTransactionId ->
                if (transactionDao.deleteById(profileId, linkedTransactionId) != 1) {
                    throw TransferLinkedTransactionNotFoundException()
                }
            }
        }
    }

    private suspend fun requireAccount(profileId: Long, accountId: Long): AccountEntity {
        return accountDao.getById(profileId, accountId) ?: throw TransferAccountNotFoundException()
    }

    private suspend fun requireTransferCategory(profileId: Long): CategoryEntity {
        return categoryDao.getProtectedByType(profileId, TRANSFER_CATEGORY_TYPE)
            ?: throw TransferCategoryNotFoundException()
    }

    private suspend fun resolveExchangeRateE8(
        profileId: Long,
        fromCurrencyCode: String,
        toCurrencyCode: String,
        snapshotDate: String,
        requestedRateE8: Long?,
    ): Long {
        if (requestedRateE8 != null) {
            return requestedRateE8.takeIf { it > 0 } ?: RATE_SCALE_E8
        }
        if (fromCurrencyCode == toCurrencyCode) {
            return RATE_SCALE_E8
        }
        return try {
            currencyRatesRepository.getRate(
                profileId = profileId,
                baseCurrency = fromCurrencyCode,
                targetCurrency = toCurrencyCode,
                effectiveDate = snapshotDate,
            ).rateE8
        } catch (error: ExchangeRateNotFoundException) {
            throw TransferExchangeRateNotFoundException()
        }
    }
}

private const val TRANSFER_CATEGORY_TYPE = "transfer"
private const val TRANSACTION_TYPE_EXPENSE = "expense"
private const val TRANSACTION_TYPE_INCOME = "income"

private val RateScaleBigInteger = BigInteger.valueOf(RATE_SCALE_E8)
private val LongMaxBigInteger = BigInteger.valueOf(Long.MAX_VALUE)

private fun requireDifferentAccounts(fromAccountId: Long, toAccountId: Long) {
    if (fromAccountId == toAccountId) {
        throw TransferSameAccountException()
    }
}

private fun requirePositiveAmount(amountCents: Long) {
    if (amountCents <= 0) {
        throw InvalidTransferAmountException()
    }
}

private fun Long.convertByRate(rateE8: Long): Long {
    val converted = BigInteger.valueOf(this)
        .multiply(BigInteger.valueOf(rateE8))
        .divide(RateScaleBigInteger)

    return when {
        converted <= BigInteger.ZERO -> throw InvalidTransferAmountException()
        converted > LongMaxBigInteger -> throw TransferAmountOverflowException()
        else -> converted.toLong()
    }
}

private fun TransferQuery.normalized(): TransferQuery {
    val safePageSize = if (pageSize in 1..MAX_TRANSFER_PAGE_SIZE) {
        pageSize
    } else {
        DEFAULT_TRANSFER_PAGE_SIZE
    }
    return copy(
        page = page.coerceAtLeast(1),
        pageSize = safePageSize,
    )
}

private fun Int.totalPages(pageSize: Int): Int {
    if (this <= 0) {
        return 1
    }
    return (this + pageSize - 1) / pageSize
}

private fun Long.toUtcSnapshotDate(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}

private fun TransferWithAccounts.toTransfer(): Transfer {
    return Transfer(
        id = transfer.id,
        profileId = transfer.profileId,
        fromAccountId = transfer.fromAccountId,
        fromAccountName = fromAccountName,
        toAccountId = transfer.toAccountId,
        toAccountName = toAccountName,
        amountCents = transfer.amountCents,
        fromCurrencyCode = transfer.fromCurrencyCode,
        toCurrencyCode = transfer.toCurrencyCode,
        exchangeRateE8 = transfer.exchangeRateE8,
        note = transfer.note,
        fromTransactionId = transfer.fromTransactionId,
        toTransactionId = transfer.toTransactionId,
        createdAtEpochMillis = transfer.createdAtEpochMillis,
    )
}
