package dev.horex.moneytracker.core.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException

private const val WRITE_TRUNCATE_MODE = "wt"

class MoneyTrackerBackupSafRepository(
    private val contentResolver: ContentResolver,
    private val exporter: MoneyTrackerBackupExporter,
    private val importer: MoneyTrackerBackupImporter,
) {
    suspend fun exportToDocument(
        uri: Uri,
        options: MoneyTrackerBackupExportOptions = MoneyTrackerBackupExportOptions(),
    ): MoneyTrackerBackupDocumentExportResult {
        val backup = exporter.exportBackup(options)
        val json = MoneyTrackerBackupV1Json.encodeToString(backup)
        val bytes = json.toByteArray(Charsets.UTF_8)
        writeBytes(uri, bytes)
        return MoneyTrackerBackupDocumentExportResult(
            exportedProfiles = backup.profiles.size,
            exportedExchangeRateSnapshots = backup.exchangeRateSnapshots.size,
            bytesWritten = bytes.size.toLong(),
        )
    }

    suspend fun exportEncryptedToDocument(
        uri: Uri,
        password: CharArray,
        options: MoneyTrackerBackupExportOptions = MoneyTrackerBackupExportOptions(),
        encryptionOptions: MoneyTrackerBackupEncryptionOptions = MoneyTrackerBackupEncryptionOptions(),
    ): MoneyTrackerBackupDocumentExportResult {
        val backup = exporter.exportBackup(options)
        val json = MoneyTrackerBackupEncryptedContainerJson.encodeEncryptedBackup(
            backup = backup,
            password = password,
            options = encryptionOptions,
        )
        val bytes = json.toByteArray(Charsets.UTF_8)
        writeBytes(uri, bytes)
        return MoneyTrackerBackupDocumentExportResult(
            exportedProfiles = backup.profiles.size,
            exportedExchangeRateSnapshots = backup.exchangeRateSnapshots.size,
            bytesWritten = bytes.size.toLong(),
        )
    }

    suspend fun restoreFromDocument(
        uri: Uri,
        options: MoneyTrackerBackupImportOptions = MoneyTrackerBackupImportOptions(),
    ): MoneyTrackerBackupImportResult {
        val json = readText(uri)
        if (MoneyTrackerBackupEncryptedContainerJson.isEncryptedContainer(json)) {
            throw MoneyTrackerBackupPasswordRequiredException()
        }
        return restoreBackupJson(json, options)
    }

    suspend fun restoreFromDocument(
        uri: Uri,
        password: CharArray,
        options: MoneyTrackerBackupImportOptions = MoneyTrackerBackupImportOptions(),
    ): MoneyTrackerBackupImportResult {
        return restoreEncryptedFromDocument(uri, password, options)
    }

    suspend fun restoreEncryptedFromDocument(
        uri: Uri,
        password: CharArray,
        options: MoneyTrackerBackupImportOptions = MoneyTrackerBackupImportOptions(),
    ): MoneyTrackerBackupImportResult {
        val containerJson = readText(uri)
        val json = MoneyTrackerBackupEncryptedContainerJson.decryptToBackupJson(containerJson, password)
        return restoreBackupJson(json, options)
    }

    private suspend fun restoreBackupJson(
        json: String,
        options: MoneyTrackerBackupImportOptions,
    ): MoneyTrackerBackupImportResult {
        val validation = MoneyTrackerBackupV1Validator.validateJson(json)
        if (!validation.canImport) {
            throw MoneyTrackerBackupImportValidationException(validation)
        }
        val backup = MoneyTrackerBackupV1Json.decodeFromString(json)
        return importer.importBackup(backup, options)
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        try {
            val outputStream = contentResolver.openOutputStream(uri, WRITE_TRUNCATE_MODE)
                ?: throw MoneyTrackerBackupDocumentException("Backup document cannot be opened for writing.")
            outputStream.use { stream ->
                stream.write(bytes)
            }
        } catch (error: IOException) {
            throw MoneyTrackerBackupDocumentException("Backup document write failed.", error)
        } catch (error: SecurityException) {
            throw MoneyTrackerBackupDocumentException("Backup document write permission was denied.", error)
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

data class MoneyTrackerBackupDocumentExportResult(
    val exportedProfiles: Int,
    val exportedExchangeRateSnapshots: Int,
    val bytesWritten: Long,
)

class MoneyTrackerBackupDocumentException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
