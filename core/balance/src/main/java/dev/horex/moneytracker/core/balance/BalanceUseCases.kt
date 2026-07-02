package dev.horex.moneytracker.core.balance

class GetAccountBalancesUseCase(
    private val repository: BalancesRepository,
) {
    suspend operator fun invoke(profileId: Long): List<BalanceAccount> {
        return repository.listAccountBalances(profileId)
    }
}

class GetBalanceSnapshotUseCase(
    private val repository: BalancesRepository,
) {
    suspend operator fun invoke(
        profileId: Long,
        query: BalanceQuery = BalanceQuery(),
    ): BalanceSnapshot {
        return repository.getBalance(profileId, query)
    }
}
