package dev.horex.moneytracker.core.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerTestFixturesTest {
    @Test
    fun accountFixturesUseLocalIdentifiersOnly() {
        val accountIds = listOf(
            MoneyTrackerTestFixtures.cashAccount.localId,
            MoneyTrackerTestFixtures.cardAccount.localId,
        )

        assertEquals(accountIds.distinct(), accountIds)
        assertTrue(accountIds.all { it > 0L })
    }

    @Test
    fun roomTestConfigUsesMigrationDatabaseName() {
        assertEquals(
            "money-tracker-migration-test.db",
            RoomTestDatabaseConfig.MigrationTestDatabaseName,
        )
    }
}
