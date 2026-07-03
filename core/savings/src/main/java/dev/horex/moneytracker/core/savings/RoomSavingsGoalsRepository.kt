package dev.horex.moneytracker.core.savings

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.GoalTransactionEntity
import dev.horex.moneytracker.core.database.model.SavingsGoalEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale

class RoomSavingsGoalsRepository(
    private val database: MoneyTrackerDatabase,
    private val goalMilestoneNotificationProcessor: GoalMilestoneNotificationProcessor? = null,
    private val goalMilestoneNotificationPreferenceProvider: GoalMilestoneNotificationPreferenceProvider =
        GoalMilestoneNotificationPreferenceProvider { false },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SavingsGoalsRepository {
    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val goalDao = database.savingsGoalDao()
    private val historyDao = database.goalTransactionDao()
    private val transactionDao = database.transactionDao()

    override suspend fun listGoals(profileId: Long): List<SavingsGoal> {
        return goalDao.listByProfile(profileId).map { goal ->
            goal.toGoal(currentCents = historyDao.getCurrentCents(profileId, goal.id))
        }
    }

    override suspend fun getGoal(profileId: Long, goalId: Long): SavingsGoal {
        val goal = requireGoal(profileId, goalId)
        return goal.toGoal(currentCents = historyDao.getCurrentCents(profileId, goal.id))
    }

    override suspend fun createGoal(profileId: Long, input: CreateSavingsGoalInput): SavingsGoal {
        val normalized = input.normalized()
        val now = normalized.createdAtEpochMillis ?: clock()

        val goalId = database.withTransaction {
            normalized.accountId?.let { accountId -> requireAccount(profileId, accountId) }
            goalDao.insert(
                SavingsGoalEntity(
                    profileId = profileId,
                    name = normalized.name,
                    targetCents = normalized.targetCents,
                    currentCents = 0L,
                    currencyCode = normalized.currencyCode,
                    deadlineDate = normalized.deadlineDate,
                    accountId = normalized.accountId,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }

        return getGoal(profileId, goalId)
    }

    override suspend fun updateGoal(
        profileId: Long,
        goalId: Long,
        input: UpdateSavingsGoalInput,
    ): SavingsGoal {
        input.validateLinkIntent()
        input.targetCents?.let(::requirePositiveAmount)
        val now = clock()

        database.withTransaction {
            val existing = requireGoal(profileId, goalId)
            val linkedAccountId = when {
                input.clearLinkedAccount -> null
                input.accountId != null -> requireAccount(profileId, input.accountId).id
                else -> existing.accountId
            }
            val deadlineDate = when {
                input.clearDeadline -> null
                input.deadlineDate != null -> input.deadlineDate.normalizedDateOrNull()
                else -> existing.deadlineDate
            }
            val updated = existing.copy(
                name = input.name?.normalizedName() ?: existing.name,
                targetCents = input.targetCents ?: existing.targetCents,
                currencyCode = input.currencyCode.normalizedCurrencyCodeOrNull() ?: existing.currencyCode,
                deadlineDate = deadlineDate,
                accountId = linkedAccountId,
                updatedAtEpochMillis = now,
            )
            if (goalDao.updateAndReturnCount(updated) != 1) {
                throw SavingsGoalNotFoundException()
            }
        }

        return getGoal(profileId, goalId)
    }

    override suspend fun deleteGoal(profileId: Long, goalId: Long) {
        if (goalDao.deleteById(profileId, goalId) != 1) {
            throw SavingsGoalNotFoundException()
        }
    }

    override suspend fun deposit(
        profileId: Long,
        goalId: Long,
        input: SavingsGoalOperationInput,
    ): SavingsGoal {
        return applyOperation(
            profileId = profileId,
            goalId = goalId,
            input = input,
            type = SavingsGoalTransactionType.Deposit,
        )
    }

    override suspend fun withdraw(
        profileId: Long,
        goalId: Long,
        input: SavingsGoalOperationInput,
    ): SavingsGoal {
        return applyOperation(
            profileId = profileId,
            goalId = goalId,
            input = input,
            type = SavingsGoalTransactionType.Withdraw,
        )
    }

    override suspend fun listHistory(profileId: Long, goalId: Long): List<SavingsGoalHistoryEntry> {
        requireGoal(profileId, goalId)
        return historyDao.listByGoal(profileId, goalId).map(GoalTransactionEntity::toHistoryEntry)
    }

    private suspend fun applyOperation(
        profileId: Long,
        goalId: Long,
        input: SavingsGoalOperationInput,
        type: SavingsGoalTransactionType,
    ): SavingsGoal {
        requirePositiveAmount(input.amountCents)
        val createdAt = input.createdAtEpochMillis ?: clock()
        var currentBefore = 0L
        var milestoneBaselineCents = 0L

        database.withTransaction {
            val goal = requireGoal(profileId, goalId)
            currentBefore = historyDao.getCurrentCents(profileId, goalId)
            milestoneBaselineCents = maxOf(
                currentBefore,
                savingsGoalHistoryMaxCurrentCents(
                    historyDao.listByGoal(profileId, goalId)
                        .map(GoalTransactionEntity::toHistoryEntry)
                        .sortedWith(compareBy<SavingsGoalHistoryEntry> { it.createdAtEpochMillis }.thenBy { it.id }),
                ),
            )
            if (type == SavingsGoalTransactionType.Withdraw && currentBefore < input.amountCents) {
                throw SavingsGoalInsufficientFundsException()
            }

            goal.accountId?.let { accountId ->
                val account = requireAccount(profileId, accountId)
                val savingsCategory = requireSavingsCategory(profileId)
                transactionDao.insert(
                    TransactionEntity(
                        profileId = profileId,
                        type = type.linkedTransactionType,
                        amountCents = input.amountCents,
                        categoryId = savingsCategory.id,
                        accountId = account.id,
                        note = input.note.normalizedOperationNote() ?: goal.name,
                        currencyCode = account.currencyCode,
                        snapshotDate = createdAt.toUtcSnapshotDate(),
                        isAdjustment = false,
                        createdAtEpochMillis = createdAt,
                    ),
                )
            }

            historyDao.insert(
                GoalTransactionEntity(
                    profileId = profileId,
                    goalId = goal.id,
                    type = type.storageValue,
                    amountCents = input.amountCents,
                    createdAtEpochMillis = createdAt,
                ),
            )
            val currentAfter = historyDao.getCurrentCents(profileId, goal.id)
            if (goalDao.updateCurrentCents(profileId, goal.id, currentAfter, createdAt) != 1) {
                throw SavingsGoalNotFoundException()
            }
        }

        val updatedGoal = getGoal(profileId, goalId)
        notifyGoalMilestoneIfNeeded(profileId, previousCurrentCents = milestoneBaselineCents, goal = updatedGoal)
        return updatedGoal
    }

    private suspend fun notifyGoalMilestoneIfNeeded(
        profileId: Long,
        previousCurrentCents: Long,
        goal: SavingsGoal,
    ) {
        val processor = goalMilestoneNotificationProcessor ?: return
        runCatching {
            val notificationsEnabled = goalMilestoneNotificationPreferenceProvider
                .areGoalMilestoneNotificationsEnabled(profileId)
            processor.run(
                goal = goal,
                previousCurrentCents = previousCurrentCents,
                goalMilestoneNotificationsEnabled = notificationsEnabled,
            )
        }
    }

    private suspend fun requireGoal(profileId: Long, goalId: Long): SavingsGoalEntity {
        return goalDao.getById(profileId, goalId) ?: throw SavingsGoalNotFoundException()
    }

    private suspend fun requireAccount(profileId: Long, accountId: Long): AccountEntity {
        return accountDao.getById(profileId, accountId) ?: throw SavingsGoalAccountNotFoundException()
    }

    private suspend fun requireSavingsCategory(profileId: Long): CategoryEntity {
        return categoryDao.listByType(profileId, SAVINGS_CATEGORY_TYPE).firstOrNull()
            ?: throw SavingsGoalCategoryNotFoundException()
    }
}

private const val SAVINGS_CATEGORY_TYPE = "savings"
private const val TRANSACTION_TYPE_EXPENSE = "expense"
private const val TRANSACTION_TYPE_INCOME = "income"
private val CurrencyCodePattern = Regex("[A-Z]{3}")

private val SavingsGoalTransactionType.linkedTransactionType: String
    get() = when (this) {
        SavingsGoalTransactionType.Deposit -> TRANSACTION_TYPE_EXPENSE
        SavingsGoalTransactionType.Withdraw -> TRANSACTION_TYPE_INCOME
    }

private fun CreateSavingsGoalInput.normalized(): CreateSavingsGoalInput {
    return copy(
        name = name.normalizedName(),
        targetCents = targetCents.also(::requirePositiveAmount),
        currencyCode = currencyCode.normalizedCurrencyCode(),
        deadlineDate = deadlineDate.normalizedDateOrNull(),
    )
}

private fun UpdateSavingsGoalInput.validateLinkIntent() {
    if (accountId != null && clearLinkedAccount) {
        throw SavingsGoalLinkConflictException()
    }
}

private fun String.normalizedName(): String {
    val normalized = trim()
    if (normalized.isEmpty()) {
        throw SavingsGoalNameEmptyException()
    }
    return normalized
}

private fun String?.normalizedOperationNote(): String? {
    return this?.trim()?.takeIf { it.isNotEmpty() }
}

private fun String.normalizedCurrencyCode(): String {
    return trim()
        .uppercase(Locale.US)
        .takeIf { CurrencyCodePattern.matches(it) }
        ?: throw InvalidSavingsGoalCurrencyException()
}

private fun String?.normalizedCurrencyCodeOrNull(): String? {
    return this?.normalizedCurrencyCode()
}

private fun String?.normalizedDateOrNull(): String? {
    val trimmed = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        LocalDate.parse(trimmed).toString()
    } catch (error: DateTimeParseException) {
        throw InvalidSavingsGoalDeadlineException()
    }
}

private fun requirePositiveAmount(amountCents: Long) {
    if (amountCents <= 0L) {
        throw InvalidSavingsGoalAmountException()
    }
}

private fun Long.toUtcSnapshotDate(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}

private fun SavingsGoalEntity.toGoal(currentCents: Long): SavingsGoal {
    return SavingsGoal(
        id = id,
        profileId = profileId,
        name = name,
        targetCents = targetCents,
        currentCents = currentCents,
        currencyCode = currencyCode,
        deadlineDate = deadlineDate,
        accountId = accountId,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun GoalTransactionEntity.toHistoryEntry(): SavingsGoalHistoryEntry {
    return SavingsGoalHistoryEntry(
        id = id,
        profileId = profileId,
        goalId = goalId,
        type = SavingsGoalTransactionType.fromStorageValue(type),
        amountCents = amountCents,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
