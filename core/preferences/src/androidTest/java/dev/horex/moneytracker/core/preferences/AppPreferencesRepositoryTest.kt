package dev.horex.moneytracker.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppPreferencesRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferenceFiles = mutableListOf<File>()

    @After
    fun cleanUp() {
        preferenceFiles.forEach { it.delete() }
        preferenceFiles.clear()
    }

    @Test
    fun preferencesFlowEmitsDefaultsAndUpdates() = runBlocking {
        val holder = createRepository()
        try {
            assertEquals(AppPreferences(), holder.repository.preferences.first())

            holder.repository.setActiveProfileId(42L)
            holder.repository.setTheme(AppThemePreference.Dark)
            holder.repository.setHideAmounts(true)
            holder.repository.setAnimateNumbers(false)

            assertEquals(
                AppPreferences(
                    activeProfileId = 42L,
                    theme = AppThemePreference.Dark,
                    hideAmounts = true,
                    animateNumbers = false,
                ),
                holder.repository.preferences.first(),
            )

            holder.repository.setAnimateNumbers(null)

            assertEquals(
                AppPreferences(
                    activeProfileId = 42L,
                    theme = AppThemePreference.Dark,
                    hideAmounts = true,
                    animateNumbers = null,
                ),
                holder.repository.preferences.first(),
            )
        } finally {
            holder.scope.cancel()
        }
    }

    @Test
    fun processDeathRestoreKeepsActiveProfileAndAppPrefs() = runBlocking {
        val file = createPreferenceFile()
        val first = createRepository(file)
        try {
            first.repository.setActiveProfileId(7L)
            first.repository.setTheme(AppThemePreference.Light)
            first.repository.setHideAmounts(true)
            first.repository.setAnimateNumbers(true)
        } finally {
            first.scope.cancel()
        }

        val reopened = createRepository(file)
        try {
            assertEquals(
                AppPreferences(
                    activeProfileId = 7L,
                    theme = AppThemePreference.Light,
                    hideAmounts = true,
                    animateNumbers = true,
                ),
                reopened.repository.preferences.first(),
            )
        } finally {
            reopened.scope.cancel()
        }
    }

    @Test
    fun activeProfileStorePersistsClearsAndRejectsInvalidIds() = runBlocking {
        val holder = createRepository()
        val activeProfileStore = DataStoreActiveProfileIdStore(holder.repository)
        try {
            assertNull(activeProfileStore.getActiveProfileId())

            activeProfileStore.setActiveProfileId(9L)
            assertEquals(9L, activeProfileStore.getActiveProfileId())

            try {
                activeProfileStore.setActiveProfileId(0L)
                fail("Invalid active profile id should be rejected")
            } catch (expected: IllegalArgumentException) {
                assertEquals("Active profile id must be positive", expected.message)
            }

            activeProfileStore.clearActiveProfileId()
            assertNull(activeProfileStore.getActiveProfileId())
        } finally {
            holder.scope.cancel()
        }
    }

    private fun createRepository(file: File = createPreferenceFile()): RepositoryHolder {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return RepositoryHolder(
            repository = AppPreferencesRepository(dataStore),
            scope = scope,
        )
    }

    private fun createPreferenceFile(): File {
        return File(context.filesDir, "app-preferences-${System.nanoTime()}.preferences_pb")
            .also { preferenceFiles += it }
    }

    private data class RepositoryHolder(
        val repository: AppPreferencesRepository,
        val scope: CoroutineScope,
    )
}
