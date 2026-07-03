package dev.horex.moneytracker.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

internal object MoneyTrackerBackupDocumentContracts {
    const val MIME_TYPE = "application/json"

    private val readableMimeTypes = arrayOf(
        MIME_TYPE,
        "text/json",
        "application/octet-stream",
    )

    fun defaultBackupFileName(
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ): String {
        return "money-tracker-backup-$createdAtEpochMillis.json"
    }

    class CreateBackupDocument : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String): Intent {
            val fileName = input.ifBlank { defaultBackupFileName() }
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

    class OpenBackupDocument : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, readableMimeTypes)
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
