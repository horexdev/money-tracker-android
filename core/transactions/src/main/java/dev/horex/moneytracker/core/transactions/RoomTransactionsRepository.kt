package dev.horex.moneytracker.core.transactions

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.dao.TransactionWithRelations
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import java.time.Instant
import java.time.ZoneOffset

class RoomTransactionsRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TransactionsRepository {
    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val transactionDao = database.transactionDao()

    override suspend fun addTransaction(
        profileId: Long,
        input: CreateTransactionInput,
    ): MoneyTransaction {
        requirePositiveAmount(input.amountCents)
        val createdAt = input.createdAtEpochMillis ?: clock()

        val transactionId = database.withTransaction {
            val account = requireAccount(profileId, input.accountId)
            val category = requireActiveCategory(profileId, input.categoryId)
            requireCategorySupportsType(category, input.type)

            transactionDao.insert(
                TransactionEntity(
                    profileId = profileId,
                    type = input.type.storageValue,
                    amountCents = input.amountCents,
                    categoryId = category.id,
                    accountId = account.id,
                    note = input.note,
                    currencyCode = account.currencyCode,
                    snapshotDate = createdAt.toUtcSnapshotDate(),
                    isAdjustment = false,
                    createdAtEpochMillis = createdAt,
                ),
            )
        }

        return getTransaction(profileId, transactionId)
    }

    override suspend fun getTransaction(profileId: Long, transactionId: Long): MoneyTransaction {
        return transactionDao.getVisibleWithRelations(profileId, transactionId)?.toTransaction()
            ?: throw TransactionNotFoundException()
    }

    override suspend fun listTransactions(
        profileId: Long,
        query: TransactionQuery,
    ): TransactionPage {
        val normalized = query.normalized()
        val total = transactionDao.countVisibleWithFilters(
            profileId = profileId,
            accountId = normalized.accountId,
            categoryId = normalized.categoryId,
            fromEpochMillis = normalized.fromEpochMillis,
            toEpochMillis = normalized.toEpochMillis,
        )
        val totalPages = total.totalPages(normalized.pageSize)
        val currentPage = normalized.page.coerceIn(1, totalPages)
        val offset = (currentPage - 1) * normalized.pageSize
        val transactions = transactionDao.listVisibleWithFilters(
            profileId = profileId,
            accountId = normalized.accountId,
            categoryId = normalized.categoryId,
            fromEpochMillis = normalized.fromEpochMillis,
            toEpochMillis = normalized.toEpochMillis,
            limit = normalized.pageSize,
            offset = offset,
        ).map(TransactionWithRelations::toTransaction)

        return TransactionPage(
            transactions = transactions,
            totalPages = totalPages,
            currentPage = currentPage,
        )
    }

    override suspend fun updateTransaction(
        profileId: Long,
        transactionId: Long,
        input: UpdateTransactionInput,
    ): MoneyTransaction {
        requirePositiveAmount(input.amountCents)

        database.withTransaction {
            val existing = requireMutableTransaction(profileId, transactionId)
            val type = TransactionType.fromStorageValue(existing.type)
            val category = requireActiveCategory(profileId, input.categoryId)
            requireCategorySupportsType(category, type)

            val updated = existing.copy(
                amountCents = input.amountCents,
                categoryId = category.id,
                note = input.note,
                createdAtEpochMillis = input.createdAtEpochMillis ?: existing.createdAtEpochMillis,
            )
            if (transactionDao.updateAndReturnCount(updated) != 1) {
                throw TransactionNotFoundException()
            }
        }

        return getTransaction(profileId, transactionId)
    }

    override suspend fun deleteTransaction(profileId: Long, transactionId: Long) {
        database.withTransaction {
            requireMutableTransaction(profileId, transactionId)
            if (transactionDao.deleteById(profileId, transactionId) != 1) {
                throw TransactionNotFoundException()
            }
        }
    }

    private suspend fun requireAccount(profileId: Long, accountId: Long): AccountEntity {
        return accountDao.getById(profileId, accountId) ?: throw TransactionAccountNotFoundException()
    }

    private suspend fun requireActiveCategory(profileId: Long, categoryId: Long): CategoryEntity {
        return categoryDao.getActiveById(profileId, categoryId) ?: throw TransactionCategoryNotFoundException()
    }

    private suspend fun requireMutableTransaction(profileId: Long, transactionId: Long): TransactionEntity {
        val transaction = transactionDao.getById(profileId, transactionId) ?: throw TransactionNotFoundException()
        if (transaction.isAdjustment) {
            throw AdjustmentTransactionImmutableException()
        }
        if (transactionDao.countTransferLinks(profileId, transactionId) > 0) {
            throw TransactionLinkedToTransferException()
        }
        return transaction
    }
}

private fun requirePositiveAmount(amountCents: Long) {
    if (amountCents <= 0) {
        throw InvalidTransactionAmountException()
    }
}

private fun requireCategorySupportsType(category: CategoryEntity, type: TransactionType) {
    val canUse = category.type == "both" || category.type == type.storageValue
    if (!canUse || category.type == "transfer" || category.type == "adjustment" || category.type == "savings") {
        throw TransactionCategoryTypeException()
    }
}

private fun TransactionQuery.normalized(): TransactionQuery {
    if (fromEpochMillis != null && toEpochMillis != null && fromEpochMillis > toEpochMillis) {
        throw InvalidTransactionDateRangeException()
    }
    val safePageSize = if (pageSize in 1..MAX_TRANSACTION_PAGE_SIZE) {
        pageSize
    } else {
        DEFAULT_TRANSACTION_PAGE_SIZE
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

private fun TransactionWithRelations.toTransaction(): MoneyTransaction {
    return MoneyTransaction(
        id = transaction.id,
        profileId = transaction.profileId,
        type = TransactionType.fromStorageValue(transaction.type),
        amountCents = transaction.amountCents,
        categoryId = transaction.categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        accountId = transaction.accountId,
        accountName = accountName,
        note = transaction.note,
        currencyCode = transaction.currencyCode,
        snapshotDate = transaction.snapshotDate,
        createdAtEpochMillis = transaction.createdAtEpochMillis,
        isAdjustment = transaction.isAdjustment,
    )
}
