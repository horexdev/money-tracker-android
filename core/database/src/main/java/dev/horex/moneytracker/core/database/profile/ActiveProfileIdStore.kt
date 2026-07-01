package dev.horex.moneytracker.core.database.profile

interface ActiveProfileIdStore {
    suspend fun getActiveProfileId(): Long?
    suspend fun setActiveProfileId(profileId: Long)
    suspend fun clearActiveProfileId()
}
