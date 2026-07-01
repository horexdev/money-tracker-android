package dev.horex.moneytracker.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerRoutesTest {
    @Test
    fun routeValuesAreUnique() {
        assertEquals(MoneyTrackerRoutes.all.distinct(), MoneyTrackerRoutes.all)
    }

    @Test
    fun topLevelDestinationsUseKnownRoutes() {
        val knownRoutes = MoneyTrackerRoutes.all.toSet()
        val topLevelRoutes = MoneyTrackerTopLevelDestination.entries.map { it.route }

        assertTrue(knownRoutes.containsAll(topLevelRoutes))
    }

    @Test
    fun dashboardIsTheStartDestinationContract() {
        assertEquals(MoneyTrackerRoutes.Dashboard, MoneyTrackerTopLevelDestination.Dashboard.route)
    }
}
