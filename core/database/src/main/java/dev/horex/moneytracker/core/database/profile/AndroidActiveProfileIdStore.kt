package dev.horex.moneytracker.core.database.profile

import android.content.Context

class AndroidActiveProfileIdStore(
    context: Context,
    preferencesName: String = DEFAULT_PREFERENCES_NAME,
) : ActiveProfileIdStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )

    override fun getActiveProfileId(): Long? {
        val profileId = preferences.getLong(KEY_ACTIVE_PROFILE_ID, NO_PROFILE_ID)
        return profileId.takeIf { it > NO_PROFILE_ID }
    }

    override fun setActiveProfileId(profileId: Long) {
        require(profileId > NO_PROFILE_ID) { "Active profile id must be positive" }
        check(
            preferences.edit()
                .putLong(KEY_ACTIVE_PROFILE_ID, profileId)
                .commit(),
        ) { "Active profile selection should be persisted" }
    }

    override fun clearActiveProfileId() {
        check(
            preferences.edit()
                .remove(KEY_ACTIVE_PROFILE_ID)
                .commit(),
        ) { "Active profile selection should be cleared" }
    }

    companion object {
        const val DEFAULT_PREFERENCES_NAME = "money_tracker_profile_state"

        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val NO_PROFILE_ID = 0L
    }
}
