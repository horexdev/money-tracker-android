package dev.horex.moneytracker.csv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

internal object MoneyTrackerCsvDocumentContracts {
    const val MIME_TYPE = "text/csv"

    fun defaultCsvFileName(
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ): String {
        return "money-tracker-transactions-$createdAtEpochMillis.csv"
    }

    class CreateCsvDocument : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String): Intent {
            val fileName = input.ifBlank { defaultCsvFileName() }
            return Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(MIME_TYPE)
                .putExtra(Intent.EXTRA_TITLE, fileName)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == Activity.RESULT_OK) {
                intent?.data
            } else {
                null
            }
        }
    }
}
