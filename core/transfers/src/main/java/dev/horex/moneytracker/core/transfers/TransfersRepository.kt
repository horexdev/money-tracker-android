package dev.horex.moneytracker.core.transfers

interface TransfersRepository {
    suspend fun createTransfer(profileId: Long, input: CreateTransferInput): Transfer

    suspend fun getTransfer(profileId: Long, transferId: Long): Transfer

    suspend fun listTransfers(profileId: Long, query: TransferQuery = TransferQuery()): TransferPage

    suspend fun deleteTransfer(profileId: Long, transferId: Long)
}
