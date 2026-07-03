package dev.horex.moneytracker.backup

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerBackupDocumentContractsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun createBackupDocumentUsesSafCreateDocumentIntent() {
        val intent = MoneyTrackerBackupDocumentContracts.CreateBackupDocument()
            .createIntent(context, "backup.json")

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertTrue(intent.categories.orEmpty().contains(Intent.CATEGORY_OPENABLE))
        assertEquals(MoneyTrackerBackupDocumentContracts.MIME_TYPE, intent.type)
        assertEquals("backup.json", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun openBackupDocumentUsesSafOpenDocumentIntent() {
        val intent = MoneyTrackerBackupDocumentContracts.OpenBackupDocument()
            .createIntent(context, Unit)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.categories.orEmpty().contains(Intent.CATEGORY_OPENABLE))
        assertEquals("*/*", intent.type)
        assertArrayEquals(
            arrayOf(
                MoneyTrackerBackupDocumentContracts.MIME_TYPE,
                "text/json",
                "application/octet-stream",
            ),
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES),
        )
    }
}
