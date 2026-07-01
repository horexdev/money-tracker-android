package dev.horex.moneytracker.core.database

import dev.horex.moneytracker.core.database.model.MoneyTrackerTables
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            MoneyTrackerTables.SAVINGS_GOALS,
            MoneyTrackerTables.GOAL_TRANSACTIONS,
            MoneyTrackerTables.EXCHANGE_RATE_SNAPSHOTS,
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
        assertTrue(MoneyTrackerDatabaseMigrations.ALL.isEmpty())
    }

    private fun readSchema(): String {
        assertTrue("Room schema export is missing: ${schemaFile.path}", schemaFile.exists())
        return schemaFile.readText()
    }
}
