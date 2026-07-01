package dev.horex.moneytracker.core.database.profile

interface ActiveProfileIdStore {
    fun getActiveProfileId(): Long?
    fun setActiveProfileId(profileId: Long)
    fun clearActiveProfileId()
}
