package dev.horex.moneytracker.core.recurring

interface RecurringTransactionsRepository {
    suspend fun listRecurring(profileId: Long): List<RecurringTransaction>

    suspend fun getRecurring(profileId: Long, recurringId: Long): RecurringTransaction

    suspend fun createRecurring(
        profileId: Long,
        input: CreateRecurringTransactionInput,
    ): RecurringTransaction

    suspend fun updateRecurring(
        profileId: Long,
        recurringId: Long,
        input: UpdateRecurringTransactionInput,
    ): RecurringTransaction

    suspend fun toggleRecurringActive(profileId: Long, recurringId: Long): RecurringTransaction

    suspend fun deleteRecurring(profileId: Long, recurringId: Long)

    suspend fun processDue(nowEpochMillis: Long? = null): ProcessRecurringDueResult
}
