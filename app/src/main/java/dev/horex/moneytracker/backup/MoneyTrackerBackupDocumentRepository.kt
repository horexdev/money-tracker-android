package dev.horex.moneytracker.backup

import android.content.ContentResolver
import android.net.Uri
import dev.horex.moneytracker.core.backup.MoneyTrackerBackup
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupDocumentExportResult
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupDocumentException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupEncryptedContainerJson
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupExportOptions
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupImporter
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupImportOptions
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupImportResult
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupImportSelectionException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupPasswordRequiredException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupSafRepository
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupV1Json
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupV1Validator
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupValidationResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MoneyTrackerBackupDocumentRepository(
    private val contentResolver: ContentResolver,
    private val safRepository: MoneyTrackerBackupSafRepository,
    private val importer: MoneyTrackerBackupImporter,
) {
    suspend fun exportToDocument(
        uri: Uri,
        request: MoneyTrackerBackupDocumentExportRequest,
    ): MoneyTrackerBackupDocumentExportResult {
        require(request.selectedLocalProfileIds.isNotEmpty()) {
            "At least one local profile must be selected for backup export."
        }
        val options = MoneyTrackerBackupExportOptions(
            selectedLocalProfileIds = request.selectedLocalProfileIds,
        )
        return withContext(Dispatchers.IO) {
            if (request.encrypted) {
                safRepository.exportEncryptedToDocument(
                    uri = uri,
                    password = requireNotNull(request.password),
                    options = options,
                )
            } else {
                safRepository.exportToDocument(uri = uri, options = options)
            }
        }
    }

    suspend fun loadRestorePreview(
        uri: Uri,
        password: CharArray? = null,
    ): MoneyTrackerBackupDocumentRestorePreview {
        return withContext(Dispatchers.IO) {
            val documentJson = readText(uri)
            val encrypted = MoneyTrackerBackupEncryptedContainerJson.isEncryptedContainer(documentJson)
            val backupJson = if (encrypted) {
                if (password == null) {
                    throw MoneyTrackerBackupPasswordRequiredException()
                }
                MoneyTrackerBackupEncryptedContainerJson.decryptToBackupJson(documentJson, password)
            } else {
                documentJson
            }
            val validation = MoneyTrackerBackupV1Validator.validateJson(backupJson)
            MoneyTrackerBackupDocumentRestorePreview(
                uri = uri,
                encrypted = encrypted,
                validation = validation,
                backup = if (validation.canImport) {
                    MoneyTrackerBackupV1Json.decodeFromString(backupJson)
                } else {
                    null
                },
            )
        }
    }

    suspend fun restorePreview(
        preview: MoneyTrackerBackupDocumentRestorePreview,
        selectedProfiles: List<MoneyTrackerBackupDocumentRestoreProfile>,
    ): MoneyTrackerBackupImportResult {
        return withContext(Dispatchers.IO) {
            val backup = preview.backup ?: throw IllegalStateException(
                "Backup preview must pass validation before restore.",
            )
            val labelsByRef = selectedProfiles.associate { selection ->
                selection.ref to selection.localLabel.trim()
            }
            if (labelsByRef.isEmpty()) {
                throw MoneyTrackerBackupImportSelectionException(
                    "At least one backup profile must be selected for restore.",
                )
            }
            val blankLabelRef = labelsByRef.entries.firstOrNull { it.value.isBlank() }?.key
            if (blankLabelRef != null) {
                throw MoneyTrackerBackupImportSelectionException(
                    "Restored local profile labels must not be blank.",
                )
            }
            val selectedBackup = backup.withSelectedProfileLabels(labelsByRef)
            importer.importBackup(
                backup = selectedBackup,
                options = MoneyTrackerBackupImportOptions(),
            )
        }
    }

    private fun readText(uri: Uri): String {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw MoneyTrackerBackupDocumentException("Backup document cannot be opened for reading.")
            inputStream.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
        } catch (error: IOException) {
            throw MoneyTrackerBackupDocumentException("Backup document read failed.", error)
        } catch (error: SecurityException) {
            throw MoneyTrackerBackupDocumentException("Backup document read permission was denied.", error)
        }
    }
}

data class MoneyTrackerBackupDocumentExportRequest(
    val selectedLocalProfileIds: Set<Long>,
    val encrypted: Boolean,
    val password: CharArray?,
)

data class MoneyTrackerBackupDocumentRestorePreview(
    val uri: Uri,
    val encrypted: Boolean,
    val validation: MoneyTrackerBackupValidationResult,
    val backup: MoneyTrackerBackup?,
)

data class MoneyTrackerBackupDocumentRestoreProfile(
    val ref: String,
    val localLabel: String,
)

private fun MoneyTrackerBackup.withSelectedProfileLabels(
    labelsByRef: Map<String, String>,
): MoneyTrackerBackup {
    val selectedRefs = labelsByRef.keys
    val selectedProfiles = profiles
        .filter { profile -> profile.ref in selectedRefs }
        .map { profile ->
            profile.copy(label = requireNotNull(labelsByRef[profile.ref]))
        }
    if (selectedProfiles.size != selectedRefs.size) {
        throw MoneyTrackerBackupImportSelectionException(
            "Selected backup profiles were not found.",
        )
    }
    return copy(profiles = selectedProfiles)
}
