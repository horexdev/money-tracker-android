package dev.horex.moneytracker.csv

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerCsvDocumentContractsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun createCsvDocumentUsesSafCreateDocumentIntent() {
        val intent = MoneyTrackerCsvDocumentContracts.CreateCsvDocument()
            .createIntent(context, "transactions.csv")

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertTrue(intent.categories.orEmpty().contains(Intent.CATEGORY_OPENABLE))
        assertEquals(MoneyTrackerCsvDocumentContracts.MIME_TYPE, intent.type)
        assertEquals("transactions.csv", intent.getStringExtra(Intent.EXTRA_TITLE))
    }
}
