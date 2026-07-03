package dev.horex.moneytracker.core.database.profile

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.seed.LocalProfileSeeder

fun interface LocalProfileBootstrapper {
    suspend fun ensureActiveProfile(): LocalProfile
}

class LocalProfileRepository(
    private val database: MoneyTrackerDatabase,
    private val activeProfileIdStore: ActiveProfileIdStore,
    private val defaults: LocalProfileDefaults = LocalProfileDefaults(),
    private val profileSeeder: LocalProfileSeeder? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : LocalProfileBootstrapper {
    private val profileDao = database.localProfileDao()

    override suspend fun ensureActiveProfile(): LocalProfile {
        activeProfileIdStore.getActiveProfileId()?.let { activeProfileId ->
            profileDao.getById(activeProfileId)?.let { return it.toLocalProfile().ensureSeeded() }
            activeProfileIdStore.clearActiveProfileId()
        }

        val entity = database.withTransaction {
            profileDao.getFirst() ?: createDefaultProfileEntity()
        }
        activeProfileIdStore.setActiveProfileId(entity.id)
        return entity.toLocalProfile().ensureSeeded()
    }

    suspend fun listProfiles(): List<LocalProfile> {
        return profileDao.list().map { it.toLocalProfile() }
    }

    suspend fun createProfile(
        label: String,
        languageCode: String = defaults.normalizedLanguageCode,
    ): LocalProfile {
        val trimmedLabel = label.trim()
        require(trimmedLabel.isNotEmpty()) { "Local profile label must not be blank" }

        val now = clock()
        val entity = LocalProfileEntity(
            label = trimmedLabel,
            languageCode = normalizeLocalProfileLanguageCode(languageCode),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val profileId = profileDao.insert(entity)
        return requireInsertedProfile(profileId).toLocalProfile().ensureSeeded()
    }

    suspend fun updateProfileLabel(profileId: Long, label: String): LocalProfile {
        val trimmedLabel = label.trim()
        require(trimmedLabel.isNotEmpty()) { "Local profile label must not be blank" }

        val updatedProfile = database.withTransaction {
            val existing = profileDao.getById(profileId)
                ?: throw IllegalArgumentException("Local profile must exist before it can be renamed")
            val updated = existing.copy(
                label = trimmedLabel,
                updatedAtEpochMillis = clock(),
            )
            if (profileDao.updateAndReturnCount(updated) != 1) {
                throw IllegalArgumentException("Local profile must exist before it can be renamed")
            }
            updated
        }

        return updatedProfile.toLocalProfile()
    }

    suspend fun selectActiveProfile(profileId: Long): LocalProfile {
        val profile = profileDao.getById(profileId)
            ?: throw IllegalArgumentException("Local profile must exist before it can be selected")
        activeProfileIdStore.setActiveProfileId(profile.id)
        return profile.toLocalProfile().ensureSeeded()
    }

    suspend fun resetActiveProfileData(): LocalProfile {
        val activeProfile = ensureActiveProfile()
        val replacement = database.withTransaction {
            val now = clock()
            val replacementId = profileDao.insert(
                LocalProfileEntity(
                    label = defaults.label,
                    languageCode = activeProfile.languageCode,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            profileDao.getById(activeProfile.id)?.let { profileDao.delete(it) }
            requireInsertedProfile(replacementId)
        }

        activeProfileIdStore.setActiveProfileId(replacement.id)
        return replacement.toLocalProfile().ensureSeeded()
    }

    private suspend fun createDefaultProfileEntity(): LocalProfileEntity {
        val now = clock()
        val entity = LocalProfileEntity(
            label = defaults.label,
            languageCode = defaults.normalizedLanguageCode,
            displayCurrenciesCsv = defaults.displayCurrencies.toCurrencyCsv(),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val profileId = profileDao.insert(entity)
        return requireInsertedProfile(profileId)
    }

    private suspend fun requireInsertedProfile(profileId: Long): LocalProfileEntity {
        return profileDao.getById(profileId)
            ?: error("Inserted local profile should be readable")
    }

    private suspend fun LocalProfile.ensureSeeded(): LocalProfile {
        profileSeeder?.ensureSeed(this)
        return this
    }
}

private fun LocalProfileEntity.toLocalProfile(): LocalProfile {
    return LocalProfile(
        id = id,
        label = label,
        languageCode = languageCode,
        displayCurrencies = displayCurrenciesCsv.fromCurrencyCsv(),
        notifyBudgetAlerts = notifyBudgetAlerts,
        notifyRecurringReminders = notifyRecurringReminders,
        notifyWeeklySummary = notifyWeeklySummary,
        notifyGoalMilestones = notifyGoalMilestones,
        statsChartStyle = statsChartStyle,
        animateNumbers = animateNumbers,
        theme = theme,
        hideAmounts = hideAmounts,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun List<String>.toCurrencyCsv(): String {
    return map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(separator = ",")
}

private fun String.fromCurrencyCsv(): List<String> {
    return split(',')
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .distinct()
}
