package dev.horex.moneytracker.csv

import android.content.ContentResolver
import android.net.Uri
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupDocumentException
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileRepository
import dev.horex.moneytracker.core.transactions.TransactionCsvExportFilters
import dev.horex.moneytracker.core.transactions.TransactionCsvExporter
import dev.horex.moneytracker.core.transactions.TransactionCsvProfileSelection
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WRITE_TRUNCATE_MODE = "wt"

class MoneyTrackerCsvDocumentRepository(
    private val contentResolver: ContentResolver,
    private val localProfileRepository: LocalProfileRepository,
    private val exporter: TransactionCsvExporter,
) {
    suspend fun exportTransactions(
        uri: Uri,
        request: MoneyTrackerCsvDocumentExportRequest,
    ): MoneyTrackerCsvDocumentExportResult {
        return withContext(Dispatchers.IO) {
            localProfileRepository.ensureActiveProfile()
            val profileSelections = localProfileRepository.listProfiles()
                .selectedProfiles(request.selectedLocalProfileIds)
                .map { profile ->
                    TransactionCsvProfileSelection(
                        profileId = profile.id,
                        profileLabel = profile.label,
                    )
                }
            val export = exporter.exportTransactions(
                profiles = profileSelections,
                filters = request.filters,
            )
            writeBytes(uri, export.bytes)
            MoneyTrackerCsvDocumentExportResult(
                exportedRows = export.rowCount,
                bytesWritten = export.bytesWritten,
            )
        }
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        try {
            val outputStream = contentResolver.openOutputStream(uri, WRITE_TRUNCATE_MODE)
                ?: throw MoneyTrackerBackupDocumentException("CSV document cannot be opened for writing.")
            outputStream.use { stream ->
                stream.write(bytes)
            }
        } catch (error: IOException) {
            throw MoneyTrackerBackupDocumentException("CSV document write failed.", error)
        } catch (error: SecurityException) {
            throw MoneyTrackerBackupDocumentException("CSV document write permission was denied.", error)
        }
    }
}

data class MoneyTrackerCsvDocumentExportRequest(
    val selectedLocalProfileIds: Set<Long>,
    val filters: TransactionCsvExportFilters = TransactionCsvExportFilters(),
)

data class MoneyTrackerCsvDocumentExportResult(
    val exportedRows: Int,
    val bytesWritten: Long,
)

private fun List<LocalProfile>.selectedProfiles(
    selectedLocalProfileIds: Set<Long>,
): List<LocalProfile> {
    require(selectedLocalProfileIds.isNotEmpty()) {
        "At least one local profile must be selected for CSV export."
    }

    val availableIds = mapTo(linkedSetOf()) { it.id }
    val missingIds = selectedLocalProfileIds
        .filterNot { it in availableIds }
        .sorted()
    require(missingIds.isEmpty()) {
        "Selected local profiles were not found: ${missingIds.joinToString()}"
    }

    return filter { profile -> profile.id in selectedLocalProfileIds }
}
