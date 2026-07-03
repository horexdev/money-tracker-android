package dev.horex.moneytracker.core.backup

class MoneyTrackerBackupContractException(message: String) : IllegalArgumentException(message)

object MoneyTrackerBackupV1Contract {
    fun requireValidExportRefs(backup: MoneyTrackerBackup) {
        val firstError = MoneyTrackerBackupV1Validator.validate(backup).errors.firstOrNull()
        if (firstError != null) {
            throw MoneyTrackerBackupContractException(firstError.message)
        }
    }
}
