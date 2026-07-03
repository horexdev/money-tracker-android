package dev.horex.moneytracker.core.recurring

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.dao.RecurringTransactionWithCategory
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionRunEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.transactions.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class RoomRecurringTransactionsRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val dueBatchSize: Int = DEFAULT_RECURRING_DUE_LIMIT,
) : RecurringTransactionsRepository {
    init {
        require(dueBatchSize > 0) { "Recurring due batch size must be positive" }
    }

    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val recurringDao = database.recurringTransactionDao()
    private val recurringRunDao = database.recurringTransactionRunDao()
    private val transactionDao = database.transactionDao()

    override suspend fun listRecurring(profileId: Long): List<RecurringTransaction> {
        return recurringDao.listWithCategoryByProfile(profileId).map(RecurringTransactionWithCategory::toRecurring)
    }

    override suspend fun getRecurring(profileId: Long, recurringId: Long): RecurringTransaction {
        return recurringDao.getWithCategoryById(profileId, recurringId)?.toRecurring()
            ?: throw RecurringTransactionNotFoundException()
    }

    override suspend fun createRecurring(
        profileId: Long,
        input: CreateRecurringTransactionInput,
    ): RecurringTransaction {
        requirePositiveAmount(input.amountCents)
        input.nextRunAtEpochMillis?.let(::requireValidNextRun)

        val now = clock()
        val recurringId = database.withTransaction {
            val account = requireAccount(profileId, input.accountId)
            val category = requireActiveCategory(profileId, input.categoryId)
            requireCategorySupportsType(category, input.type)
            val nextRunAt = input.nextRunAtEpochMillis ?: input.frequency.nextRunAfter(now, zoneId)
            requireValidNextRun(nextRunAt)

            recurringDao.insert(
                RecurringTransactionEntity(
                    profileId = profileId,
                    accountId = account.id,
                    categoryId = category.id,
                    type = input.type.storageValue,
                    amountCents = input.amountCents,
                    currencyCode = account.currencyCode,
                    note = input.note,
                    frequency = input.frequency.storageValue,
                    nextRunAtEpochMillis = nextRunAt,
                    isActive = true,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }

        return getRecurring(profileId, recurringId)
    }

    override suspend fun updateRecurring(
        profileId: Long,
        recurringId: Long,
        input: UpdateRecurringTransactionInput,
    ): RecurringTransaction {
        input.amountCents?.let(::requirePositiveAmount)
        input.nextRunAtEpochMillis?.let(::requireValidNextRun)
        val now = clock()

        database.withTransaction {
            val existing = recurringDao.getById(profileId, recurringId)
                ?: throw RecurringTransactionNotFoundException()
            val type = input.type ?: TransactionType.fromStorageValue(existing.type)
            val account = requireAccount(profileId, input.accountId ?: existing.accountId)
            val category = requireActiveCategory(profileId, input.categoryId ?: existing.categoryId)
            requireCategorySupportsType(category, type)
            val frequency = input.frequency ?: RecurringFrequency.fromStorageValue(existing.frequency)
            val amountCents = input.amountCents ?: existing.amountCents
            val nextRunAt = input.nextRunAtEpochMillis ?: existing.nextRunAtEpochMillis
            requirePositiveAmount(amountCents)
            requireValidNextRun(nextRunAt)

            val updated = existing.copy(
                accountId = account.id,
                categoryId = category.id,
                type = type.storageValue,
                amountCents = amountCents,
                currencyCode = account.currencyCode,
                note = input.note ?: existing.note,
                frequency = frequency.storageValue,
                nextRunAtEpochMillis = nextRunAt,
                updatedAtEpochMillis = now,
            )
            if (recurringDao.updateAndReturnCount(updated) != 1) {
                throw RecurringTransactionNotFoundException()
            }
        }

        return getRecurring(profileId, recurringId)
    }

    override suspend fun toggleRecurringActive(profileId: Long, recurringId: Long): RecurringTransaction {
        val now = clock()
        if (recurringDao.toggleActive(profileId, recurringId, now) != 1) {
            throw RecurringTransactionNotFoundException()
        }
        return getRecurring(profileId, recurringId)
    }

    override suspend fun deleteRecurring(profileId: Long, recurringId: Long) {
        if (recurringDao.deleteById(profileId, recurringId) != 1) {
            throw RecurringTransactionNotFoundException()
        }
    }

    override suspend fun processDue(nowEpochMillis: Long?): ProcessRecurringDueResult {
        val now = nowEpochMillis ?: clock()
        requireValidNextRun(now)
        val due = recurringDao.listDue(now, dueBatchSize)

        var processed = 0
        var skipped = 0
        for (item in due) {
            val result = runCatching { processOneDue(item.profileId, item.id, now) }
                .getOrElse { error ->
                    if (error is RecurringException || error is IllegalArgumentException) {
                        DueProcessStatus.Skipped
                    } else {
                        throw error
                    }
                }
            when (result) {
                DueProcessStatus.Processed -> processed += 1
                DueProcessStatus.Skipped,
                DueProcessStatus.AlreadyProcessed -> skipped += 1
            }
        }

        return ProcessRecurringDueResult(
            processedCount = processed,
            skippedCount = skipped,
        )
    }

    private suspend fun processOneDue(
        profileId: Long,
        recurringId: Long,
        nowEpochMillis: Long,
    ): DueProcessStatus {
        return database.withTransaction {
            val current = recurringDao.getById(profileId, recurringId)
                ?: return@withTransaction DueProcessStatus.Skipped
            if (!current.isActive || current.nextRunAtEpochMillis > nowEpochMillis) {
                return@withTransaction DueProcessStatus.Skipped
            }

            val scheduledFor = current.nextRunAtEpochMillis
            val frequency = RecurringFrequency.fromStorageValue(current.frequency)
            val nextRunAt = frequency.nextRunAfter(nowEpochMillis, zoneId)
            val runId = recurringRunDao.insertIgnore(
                RecurringTransactionRunEntity(
                    profileId = current.profileId,
                    recurringTransactionId = current.id,
                    scheduledForEpochMillis = scheduledFor,
                    transactionId = null,
                    processedAtEpochMillis = nowEpochMillis,
                ),
            )
            if (runId == INSERT_IGNORED) {
                recurringDao.advanceNextRunIfCurrent(
                    profileId = current.profileId,
                    recurringId = current.id,
                    expectedCurrentRunAtEpochMillis = scheduledFor,
                    nextRunAtEpochMillis = nextRunAt,
                    updatedAtEpochMillis = nowEpochMillis,
                )
                return@withTransaction DueProcessStatus.AlreadyProcessed
            }

            val account = requireAccount(current.profileId, current.accountId)
            val category = requireActiveCategory(current.profileId, current.categoryId)
            val type = TransactionType.fromStorageValue(current.type)
            requireCategorySupportsType(category, type)
            val transactionId = transactionDao.insert(
                TransactionEntity(
                    profileId = current.profileId,
                    type = type.storageValue,
                    amountCents = current.amountCents,
                    categoryId = category.id,
                    accountId = account.id,
                    note = current.note,
                    currencyCode = account.currencyCode,
                    snapshotDate = nowEpochMillis.toUtcSnapshotDate(),
                    isAdjustment = false,
                    createdAtEpochMillis = nowEpochMillis,
                ),
            )
            check(recurringRunDao.attachTransaction(runId, transactionId) == 1) {
                "Recurring run marker must exist while processing due item"
            }
            if (
                recurringDao.advanceNextRunIfCurrent(
                    profileId = current.profileId,
                    recurringId = current.id,
                    expectedCurrentRunAtEpochMillis = scheduledFor,
                    nextRunAtEpochMillis = nextRunAt,
                    updatedAtEpochMillis = nowEpochMillis,
                ) != 1
            ) {
                throw RecurringTransactionNotFoundException()
            }
            DueProcessStatus.Processed
        }
    }

    private suspend fun requireAccount(profileId: Long, accountId: Long): AccountEntity {
        return accountDao.getById(profileId, accountId) ?: throw RecurringAccountNotFoundException()
    }

    private suspend fun requireActiveCategory(profileId: Long, categoryId: Long): CategoryEntity {
        return categoryDao.getActiveById(profileId, categoryId) ?: throw RecurringCategoryNotFoundException()
    }
}

private enum class DueProcessStatus {
    Processed,
    AlreadyProcessed,
    Skipped,
}

private const val INSERT_IGNORED = -1L
private const val BOTH_CATEGORY_TYPE = "both"
private const val TRANSFER_CATEGORY_TYPE = "transfer"
private const val ADJUSTMENT_CATEGORY_TYPE = "adjustment"
private const val SAVINGS_CATEGORY_TYPE = "savings"

private fun requirePositiveAmount(amountCents: Long) {
    if (amountCents <= 0) {
        throw InvalidRecurringAmountException()
    }
}

private fun requireValidNextRun(nextRunAtEpochMillis: Long) {
    if (nextRunAtEpochMillis < 0L) {
        throw InvalidRecurringNextRunException()
    }
}

private fun requireCategorySupportsType(category: CategoryEntity, type: TransactionType) {
    val canUse = category.type == BOTH_CATEGORY_TYPE || category.type == type.storageValue
    if (
        !canUse ||
        category.type == TRANSFER_CATEGORY_TYPE ||
        category.type == ADJUSTMENT_CATEGORY_TYPE ||
        category.type == SAVINGS_CATEGORY_TYPE
    ) {
        throw RecurringCategoryTypeException()
    }
}

private fun RecurringTransactionWithCategory.toRecurring(): RecurringTransaction {
    return RecurringTransaction(
        id = recurring.id,
        profileId = recurring.profileId,
        accountId = recurring.accountId,
        categoryId = recurring.categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        type = TransactionType.fromStorageValue(recurring.type),
        amountCents = recurring.amountCents,
        currencyCode = recurring.currencyCode,
        note = recurring.note,
        frequency = RecurringFrequency.fromStorageValue(recurring.frequency),
        nextRunAtEpochMillis = recurring.nextRunAtEpochMillis,
        isActive = recurring.isActive,
        createdAtEpochMillis = recurring.createdAtEpochMillis,
        updatedAtEpochMillis = recurring.updatedAtEpochMillis,
    )
}

private fun Long.toUtcSnapshotDate(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}
