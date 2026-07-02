package dev.horex.moneytracker.core.stats

interface StatsRepository {
    suspend fun getStats(profileId: Long, query: StatsQuery = StatsQuery()): StatsSnapshot
}
