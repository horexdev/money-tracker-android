package dev.horex.moneytracker.core.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

object MoneyTrackerBackupV1Json {
    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encodeToString(backup: MoneyTrackerBackup): String {
        MoneyTrackerBackupV1Contract.requireValidExportRefs(backup)
        return json.encodeToString(MoneyTrackerBackup.serializer(), backup)
    }

    fun decodeFromString(value: String): MoneyTrackerBackup {
        val backup = json.decodeFromString(MoneyTrackerBackup.serializer(), value)
        MoneyTrackerBackupV1Contract.requireValidExportRefs(backup)
        return backup
    }
}
