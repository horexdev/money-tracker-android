package dev.horex.moneytracker.core.transactions

interface TransactionsRepository {
    suspend fun addTransaction(profileId: Long, input: CreateTransactionInput): MoneyTransaction

    suspend fun getTransaction(profileId: Long, transactionId: Long): MoneyTransaction

    suspend fun listTransactions(profileId: Long, query: TransactionQuery = TransactionQuery()): TransactionPage

    suspend fun updateTransaction(
        profileId: Long,
        transactionId: Long,
        input: UpdateTransactionInput,
    ): MoneyTransaction

    suspend fun deleteTransaction(profileId: Long, transactionId: Long)
}
