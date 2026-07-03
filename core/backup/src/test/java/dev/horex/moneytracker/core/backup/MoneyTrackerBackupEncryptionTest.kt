package dev.horex.moneytracker.core.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerBackupEncryptionTest {
    @Test
    fun encryptedContainerStoresKdfParametersAndDecryptsBackupV1Payload() {
        val backup = sampleBackup()

        val encoded = MoneyTrackerBackupEncryptedContainerJson.encodeEncryptedBackup(
            backup = backup,
            password = password(),
            options = fastEncryptionOptions,
        )

        val container = MoneyTrackerBackupEncryptedContainerJson.decodeContainer(encoded)
        assertTrue(MoneyTrackerBackupEncryptedContainerJson.isEncryptedContainer(encoded))
        assertEquals(MONEY_TRACKER_BACKUP_ENCRYPTED_CONTAINER_FORMAT, container.format)
        assertEquals(MONEY_TRACKER_BACKUP_ENCRYPTED_CONTAINER_VERSION, container.version)
        assertEquals(MONEY_TRACKER_BACKUP_FORMAT, container.payloadFormat)
        assertEquals(MONEY_TRACKER_BACKUP_VERSION, container.payloadVersion)
        assertEquals(MONEY_TRACKER_BACKUP_ENCRYPTION_ALGORITHM, container.encryption.algorithm)
        assertEquals(MONEY_TRACKER_BACKUP_KDF_ALGORITHM, container.encryption.kdf.algorithm)
        assertEquals(fastEncryptionOptions.kdfIterations, container.encryption.kdf.iterations)
        assertEquals(MONEY_TRACKER_BACKUP_AES_KEY_LENGTH_BITS, container.encryption.kdf.keyLengthBits)
        assertTrue(container.encryption.kdf.saltBase64.isNotBlank())
        assertTrue(container.encryption.nonceBase64.isNotBlank())
        assertTrue(container.encryption.ciphertextBase64.isNotBlank())
        assertFalse(encoded.contains("Offline profile"))
        assertFalse(encoded.contains("profile:main"))
        assertFalse(encoded.contains("portable-backup-password"))

        val decoded = MoneyTrackerBackupEncryptedContainerJson.decodeEncryptedBackup(encoded, password())

        assertEquals(backup, decoded)
    }

    @Test
    fun encryptedBackupCanBeImportedAfterPasswordDecryption() = runTest {
        val encoded = MoneyTrackerBackupEncryptedContainerJson.encodeEncryptedBackup(
            backup = sampleBackup(),
            password = password(),
            options = fastEncryptionOptions,
        )
        val store = RecordingImportStore()
        val importer = MoneyTrackerBackupImporter(store)

        val result = importer.importBackup(
            MoneyTrackerBackupEncryptedContainerJson.decodeEncryptedBackup(encoded, password()),
        )

        assertEquals(1, store.transactionsStarted)
        assertEquals(1, result.importedProfileCount)
        assertEquals("Offline profile", store.profiles.single().label)
    }

    @Test
    fun wrongPasswordFailsWithClearError() {
        val encoded = MoneyTrackerBackupEncryptedContainerJson.encodeEncryptedBackup(
            backup = sampleBackup(),
            password = password(),
            options = fastEncryptionOptions,
        )

        val error = assertThrows(MoneyTrackerBackupWrongPasswordException::class.java) {
            MoneyTrackerBackupEncryptedContainerJson.decodeEncryptedBackup(encoded, "wrong-password".toCharArray())
        }

        assertTrue(error.message.orEmpty().contains("password is incorrect"))
    }

    @Test
    fun decryptedPayloadStillRejectsSourceIdentityFields() {
        val plainJson = MoneyTrackerBackupV1Json.encodeToString(sampleBackup())
            .replace(
                """"format": "MoneyTrackerBackup",""",
                """"format": "MoneyTrackerBackup",
  "telegram_id": 123456,""",
            )
        val encoded = MoneyTrackerBackupEncryptedContainerJson.encodeEncryptedBackupJson(
            backupJson = plainJson,
            password = password(),
            options = fastEncryptionOptions,
        )

        val error = assertThrows(MoneyTrackerBackupEncryptedPayloadValidationException::class.java) {
            MoneyTrackerBackupEncryptedContainerJson.decodeEncryptedBackup(encoded, password())
        }

        assertTrue(
            error.validationResult.errors.any {
                it.code == MoneyTrackerBackupValidationCode.ForbiddenSourceIdentityField &&
                    it.path == "$.telegram_id"
            },
        )
    }

    @Test
    fun blankPasswordIsRejectedBeforeEncryption() {
        val error = assertThrows(MoneyTrackerBackupInvalidContainerException::class.java) {
            MoneyTrackerBackupEncryptedContainerJson.encodeEncryptedBackup(
                backup = sampleBackup(),
                password = "   ".toCharArray(),
                options = fastEncryptionOptions,
            )
        }

        assertTrue(error.message.orEmpty().contains("password must not be blank"))
    }

    private fun sampleBackup(): MoneyTrackerBackup {
        return MoneyTrackerBackup(
            createdAtEpochMillis = TEST_TIME,
            profiles = listOf(
                BackupProfile(
                    ref = "profile:main",
                    label = "Offline profile",
                    languageCode = "en",
                    createdAtEpochMillis = TEST_TIME,
                    updatedAtEpochMillis = TEST_TIME,
                ),
            ),
        )
    }

    private fun password(): CharArray = "portable-backup-password".toCharArray()

    private companion object {
        const val TEST_TIME = 1_788_200_000_000L
        val fastEncryptionOptions = MoneyTrackerBackupEncryptionOptions(kdfIterations = 1_000)
    }
}

private class RecordingImportStore : MoneyTrackerBackupImportStore {
    var transactionsStarted = 0
        private set
    val profiles = mutableListOf<RecordingProfile>()

    override suspend fun <T> withTransaction(block: suspend MoneyTrackerBackupImportStore.() -> T): T {
        transactionsStarted += 1
        return this.block()
    }

    override suspend fun insertProfile(profile: BackupProfile): Long {
        profiles += RecordingProfile(id = 1, label = profile.label)
        return 1
    }

    override suspend fun insertAccount(profileId: Long, account: BackupAccount): Long {
        error("No accounts expected in encryption import test.")
    }

    override suspend fun insertCategory(profileId: Long, category: BackupCategory): Long {
        error("No categories expected in encryption import test.")
    }

    override suspend fun insertTransaction(
        profileId: Long,
        transaction: BackupTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        error("No transactions expected in encryption import test.")
    }

    override suspend fun insertTransfer(
        profileId: Long,
        transfer: BackupTransfer,
        fromAccountId: Long,
        toAccountId: Long,
        fromTransactionId: Long?,
        toTransactionId: Long?,
    ): Long {
        error("No transfers expected in encryption import test.")
    }

    override suspend fun insertBudget(profileId: Long, budget: BackupBudget, categoryId: Long): Long {
        error("No budgets expected in encryption import test.")
    }

    override suspend fun insertRecurringTransaction(
        profileId: Long,
        recurring: BackupRecurringTransaction,
        accountId: Long,
        categoryId: Long,
    ): Long {
        error("No recurring transactions expected in encryption import test.")
    }

    override suspend fun insertSavingsGoal(profileId: Long, goal: BackupSavingsGoal, accountId: Long?): Long {
        error("No savings goals expected in encryption import test.")
    }

    override suspend fun insertGoalTransaction(
        profileId: Long,
        transaction: BackupGoalTransaction,
        goalId: Long,
    ): Long {
        error("No goal transactions expected in encryption import test.")
    }

    override suspend fun saveExchangeRateSnapshot(snapshot: BackupExchangeRateSnapshot): Long {
        error("No exchange rate snapshots expected in encryption import test.")
    }

    override suspend fun saveExchangeRateOverride(profileId: Long, override: BackupExchangeRateOverride): Long {
        error("No exchange rate overrides expected in encryption import test.")
    }

    override suspend fun insertTransactionTemplate(
        profileId: Long,
        template: BackupTransactionTemplate,
        accountId: Long,
        categoryId: Long,
    ): Long {
        error("No transaction templates expected in encryption import test.")
    }
}

private data class RecordingProfile(val id: Long, val label: String)
