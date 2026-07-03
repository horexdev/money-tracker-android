package dev.horex.moneytracker.core.database

import dev.horex.moneytracker.core.database.dao.AccountDao
import dev.horex.moneytracker.core.database.dao.CategoryDao
import dev.horex.moneytracker.core.database.dao.ExchangeRateOverrideDao
import dev.horex.moneytracker.core.database.dao.ExchangeRateSnapshotDao
import dev.horex.moneytracker.core.database.dao.LocalProfileDao
import dev.horex.moneytracker.core.database.dao.SavingsGoalDao
import dev.horex.moneytracker.core.database.dao.requireDefaultAccountUpdate
import dev.horex.moneytracker.core.database.model.MoneyTrackerTables
import dev.horex.moneytracker.core.database.profile.LocalProfile
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MoneyTrackerDatabaseSchemaTest {
    private val schemaFile = File(
        "schemas/dev.horex.moneytracker.core.database.MoneyTrackerDatabase/${MoneyTrackerDatabase.SCHEMA_VERSION}.json",
    )

    @Test
    fun schemaExportsVersionOneForAllDomains() {
        val schema = readSchema()

        listOf(
            MoneyTrackerTables.LOCAL_PROFILES,
            MoneyTrackerTables.ACCOUNTS,
            MoneyTrackerTables.CATEGORIES,
            MoneyTrackerTables.TRANSACTIONS,
            MoneyTrackerTables.TRANSFERS,
            MoneyTrackerTables.BUDGETS,
            MoneyTrackerTables.RECURRING_TRANSACTIONS,
            MoneyTrackerTables.RECURRING_TRANSACTION_RUNS,
            MoneyTrackerTables.SAVINGS_GOALS,
            MoneyTrackerTables.GOAL_TRANSACTIONS,
            MoneyTrackerTables.EXCHANGE_RATE_SNAPSHOTS,
            MoneyTrackerTables.EXCHANGE_RATE_OVERRIDES,
            MoneyTrackerTables.TRANSACTION_TEMPLATES,
        ).forEach { table ->
            assertTrue("Schema should contain $table", schema.contains("\"tableName\": \"$table\""))
        }
    }

    @Test
    fun schemaDoesNotPersistSourceIdentityFields() {
        val schema = readSchema()

        listOf(
            "\"user_id\"",
            "telegram",
            "init_data",
            "bot_",
            "chat_",
            "legacy_",
            "source_",
            "\"username\"",
            "\"first_name\"",
            "\"last_name\"",
        ).forEach { forbidden ->
            assertFalse("Schema must not contain $forbidden", schema.contains(forbidden))
        }
    }

    @Test
    fun migrationListStartsAtInitialVersion() {
        assertTrue(MoneyTrackerDatabaseMigrations.INITIAL_VERSION == 1)
        assertTrue(MoneyTrackerDatabase.SCHEMA_VERSION == 2)
        assertTrue(MoneyTrackerDatabaseMigrations.ALL.isNotEmpty())
    }

    @Test
    fun schemaContainsOfflineLookupIndexes() {
        val schema = readSchema()

        listOf(
            "index_recurring_transactions_is_active_next_run_at_epoch_millis",
            "index_recurring_transaction_runs_profile_id_recurring_transaction_id_scheduled_for_epoch_millis",
            "index_exchange_rate_snapshots_base_currency_target_currency_snapshot_date",
            "index_exchange_rate_overrides_profile_id_base_currency_target_currency_effective_date",
        ).forEach { indexName ->
            assertTrue("Schema should contain $indexName", schema.contains(indexName))
        }
    }

    @Test
    fun daoContractsKeepProfileBoundaries() {
        val accountMethods = AccountDao::class.java.methods.map { it.name }.toSet()
        val categoryMethods = CategoryDao::class.java.methods.map { it.name }.toSet()
        val snapshotMethods = ExchangeRateSnapshotDao::class.java.methods.map { it.name }.toSet()
        val overrideMethods = ExchangeRateOverrideDao::class.java.methods.map { it.name }.toSet()
        val profileMethods = LocalProfileDao::class.java.methods.map { it.name }.toSet()
        val savingsGoalListByAccount = SavingsGoalDao::class.java.methods.single { it.name == "listByAccount" }

        assertTrue("LocalProfileDao should expose first-launch fallback lookup", "getFirst" in profileMethods)
        assertTrue("AccountDao should expose transactional default setter", "setDefault" in accountMethods)
        assertTrue("AccountDao should expose default clear helper", "clearOtherDefaultAccounts" in accountMethods)
        assertTrue("AccountDao should expose default mark helper", "markDefault" in accountMethods)
        assertTrue("CategoryDao should expose active scoped lookup", "getActiveById" in categoryMethods)
        assertTrue("CategoryDao should expose editable category list", "listEditableByProfile" in categoryMethods)
        assertTrue("CategoryDao should expose soft delete", "softDelete" in categoryMethods)
        assertTrue("CategoryDao should expose profile-scoped frequency sorting", "listByFrequency" in categoryMethods)
        assertTrue("ExchangeRateSnapshotDao should expose bulk seed snapshot upsert", "upsertAll" in snapshotMethods)
        assertTrue("ExchangeRateSnapshotDao should expose latest snapshot date", "getLatestSnapshotDate" in snapshotMethods)
        assertTrue("ExchangeRateOverrideDao should expose manual latest lookup", "getLatestAtOrBefore" in overrideMethods)
        assertTrue(
            "SavingsGoalDao.listByAccount should require profileId and accountId",
            savingsGoalListByAccount.parameterTypes.count { it == Long::class.javaPrimitiveType } >= 2,
        )
    }

    @Test
    fun defaultAccountUpdateGuardRejectsMissingTarget() {
        requireDefaultAccountUpdate(1)

        try {
            requireDefaultAccountUpdate(0)
            fail("Default account setter should reject missing target account")
        } catch (expected: IllegalStateException) {
            assertTrue(
                "Exception should explain the profile boundary requirement",
                expected.message.orEmpty().contains("target profile"),
            )
        }
    }

    @Test
    fun sqlCipherFactoryRejectsEmptyPassphrase() {
        try {
            MoneyTrackerDatabaseFactory.createSqlCipherFactory(ByteArray(0))
            fail("SQLCipher factory should reject an empty passphrase")
        } catch (expected: IllegalArgumentException) {
            assertTrue(
                "Exception should explain the passphrase requirement",
                expected.message.orEmpty().contains("passphrase"),
            )
        }
    }

    @Test
    fun databaseEncryptionContractUsesKeystoreAndAvoidsLogs() {
        val source = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        listOf(
            "SupportOpenHelperFactory",
            "System.loadLibrary(\"sqlcipher\")",
            "AndroidKeyStore",
            "KeyGenParameterSpec",
            "AES/GCM/NoPadding",
        ).forEach { required ->
            assertTrue("Database encryption source should contain $required", source.contains(required))
        }

        listOf(
            "android.util.Log",
            "Log.",
            "println(",
        ).forEach { forbidden ->
            assertFalse("Database module must not log plaintext financial data", source.contains(forbidden))
        }
    }

    @Test
    fun localProfileContractAvoidsSourceIdentityFields() {
        val source = File("src/main/java/dev/horex/moneytracker/core/database/profile")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText().lowercase() }
        val modelFields = LocalProfile::class.java.declaredFields
            .map { it.name.lowercase() }
            .joinToString(separator = "\n")

        listOf(
            "telegram",
            "initdata",
            "init_data",
            "userid",
            "user_id",
            "username",
            "firstname",
            "first_name",
            "lastname",
            "last_name",
            "legacy_",
            "source_",
        ).forEach { forbidden ->
            assertFalse("Local profile source must not contain $forbidden", source.contains(forbidden))
            assertFalse("Local profile model must not expose $forbidden", modelFields.contains(forbidden))
        }
    }

    private fun readSchema(): String {
        assertTrue("Room schema export is missing: ${schemaFile.path}", schemaFile.exists())
        return schemaFile.readText()
    }
}
