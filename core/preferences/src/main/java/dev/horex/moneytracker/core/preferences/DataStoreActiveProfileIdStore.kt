package dev.horex.moneytracker.core.preferences

import dev.horex.moneytracker.core.database.profile.ActiveProfileIdStore

class DataStoreActiveProfileIdStore(
    private val appPreferencesRepository: AppPreferencesRepository,
) : ActiveProfileIdStore {
    override suspend fun getActiveProfileId(): Long? {
        return appPreferencesRepository.getActiveProfileId()
    }

    override suspend fun setActiveProfileId(profileId: Long) {
        appPreferencesRepository.setActiveProfileId(profileId)
    }

    override suspend fun clearActiveProfileId() {
        appPreferencesRepository.clearActiveProfileId()
    }
}
