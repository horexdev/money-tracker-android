package dev.horex.moneytracker.core.balance

interface BalancesRepository {
    suspend fun listAccountBalances(profileId: Long): List<BalanceAccount>

    suspend fun getBalance(profileId: Long, query: BalanceQuery = BalanceQuery()): BalanceSnapshot
}
