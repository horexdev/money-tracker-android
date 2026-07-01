package dev.horex.moneytracker.core.accounts

interface AccountsRepository {
    suspend fun listAccounts(profileId: Long): List<Account>

    suspend fun getAccount(profileId: Long, accountId: Long): Account

    suspend fun getDefaultAccount(profileId: Long): Account

    suspend fun createAccount(profileId: Long, input: CreateAccountInput): Account

    suspend fun updateAccount(profileId: Long, accountId: Long, input: UpdateAccountInput): Account

    suspend fun setDefaultAccount(profileId: Long, accountId: Long): Account

    suspend fun deleteAccount(profileId: Long, accountId: Long)
}
