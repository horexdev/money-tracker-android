package dev.horex.moneytracker.backup

import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher

internal class MoneyTrackerBackupDocumentLauncher(
    activityResultCaller: ActivityResultCaller,
    onExportDocumentSelected: (Uri) -> Unit,
    onRestoreDocumentSelected: (Uri) -> Unit,
) {
    private val createDocument: ActivityResultLauncher<String> =
        activityResultCaller.registerForActivityResult(
            MoneyTrackerBackupDocumentContracts.CreateBackupDocument(),
        ) { uri ->
            uri?.let(onExportDocumentSelected)
        }

    private val openDocument: ActivityResultLauncher<Unit> =
        activityResultCaller.registerForActivityResult(
            MoneyTrackerBackupDocumentContracts.OpenBackupDocument(),
        ) { uri ->
            uri?.let(onRestoreDocumentSelected)
        }

    fun launchExport(
        fileName: String = MoneyTrackerBackupDocumentContracts.defaultBackupFileName(),
    ) {
        createDocument.launch(fileName)
    }

    fun launchRestore() {
        openDocument.launch(Unit)
    }
}
