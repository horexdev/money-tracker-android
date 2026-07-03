package dev.horex.moneytracker.core.budgets

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTest {
    @Test
    fun usagePercentAndOverLimitUseSpentAgainstLimit() {
        val partial = budget(limitCents = 10_000, spentCents = 7_500)
        val full = budget(limitCents = 10_000, spentCents = 10_000)

        assertEquals(75.0, partial.usagePercent, 0.001)
        assertFalse(partial.isOverLimit)
        assertEquals(100.0, full.usagePercent, 0.001)
        assertTrue(full.isOverLimit)
    }

    @Test
    fun thresholdsReturnCrossedFixedPercentages() {
        val underThreshold = budget(limitCents = 10_000, spentCents = 4_999)
        val crossedMany = budget(limitCents = 10_000, spentCents = 9_500)
        val alreadyNotified = budget(
            limitCents = 10_000,
            spentCents = 9_500,
            lastNotifiedPercent = 75,
            lastNotifiedAtEpochMillis = JULY_15,
        )

        assertEquals(emptyList<Int>(), underThreshold.crossedAlertThresholds(JULY_01))
        assertEquals(listOf(50, 75, 95), crossedMany.crossedAlertThresholds(JULY_01))
        assertEquals(50, crossedMany.nextAlertThreshold(JULY_01))
        assertEquals(listOf(95), alreadyNotified.crossedAlertThresholds(JULY_01))
        assertEquals(95, alreadyNotified.nextAlertThreshold(JULY_01))
    }

    @Test
    fun thresholdStateResetsWhenLastNotificationBelongsToPreviousPeriod() {
        val budget = budget(
            limitCents = 10_000,
            spentCents = 7_500,
            lastNotifiedPercent = 75,
            lastNotifiedAtEpochMillis = JUNE_30,
        )

        assertEquals(listOf(50, 75), budget.crossedAlertThresholds(JULY_01))
    }

    @Test
    fun thresholdsAreDisabledWhenNotificationsAreDisabled() {
        val budget = budget(
            limitCents = 10_000,
            spentCents = 12_000,
            notificationsEnabled = false,
        )

        assertEquals(emptyList<Int>(), budget.crossedAlertThresholds(JULY_01))
        assertEquals(0, budget.nextAlertThreshold(JULY_01))
        assertTrue(budget.isOverLimit)
    }

    @Test
    fun periodRangesUseMondayWeekAndCalendarMonthBounds() {
        val weekly = BudgetPeriod.Weekly.currentRange(JULY_15, ZoneOffset.UTC)
        val monthly = BudgetPeriod.Monthly.currentRange(JULY_15, ZoneOffset.UTC)
        val sundayWeekly = BudgetPeriod.Weekly.currentRange(JULY_19, ZoneOffset.UTC)

        assertEquals(JULY_13, weekly.fromEpochMillisInclusive)
        assertEquals(JULY_20, weekly.toEpochMillisExclusive)
        assertEquals(JULY_13, sundayWeekly.fromEpochMillisInclusive)
        assertEquals(JULY_20, sundayWeekly.toEpochMillisExclusive)
        assertEquals(JULY_01, monthly.fromEpochMillisInclusive)
        assertEquals(AUGUST_01, monthly.toEpochMillisExclusive)
    }

    private fun budget(
        limitCents: Long,
        spentCents: Long,
        notificationsEnabled: Boolean = true,
        lastNotifiedPercent: Int = 0,
        lastNotifiedAtEpochMillis: Long? = null,
    ): Budget {
        return Budget(
            id = 1,
            profileId = 1,
            categoryId = 2,
            categoryName = "Food",
            categoryIcon = "fork",
            categoryColor = "#64748b",
            limitCents = limitCents,
            spentCents = spentCents,
            period = BudgetPeriod.Monthly,
            currencyCode = "USD",
            notifyAtPercent = DEFAULT_BUDGET_NOTIFY_AT_PERCENT,
            notificationsEnabled = notificationsEnabled,
            lastNotifiedPercent = lastNotifiedPercent,
            lastNotifiedAtEpochMillis = lastNotifiedAtEpochMillis,
            createdAtEpochMillis = JULY_01,
            updatedAtEpochMillis = JULY_01,
        )
    }

    private companion object {
        val JUNE_30 = utcDate(2026, 6, 30)
        val JULY_01 = utcDate(2026, 7, 1)
        val JULY_13 = utcDate(2026, 7, 13)
        val JULY_15 = utcDate(2026, 7, 15)
        val JULY_19 = utcDate(2026, 7, 19)
        val JULY_20 = utcDate(2026, 7, 20)
        val AUGUST_01 = utcDate(2026, 8, 1)

        fun utcDate(year: Int, month: Int, dayOfMonth: Int): Long {
            return LocalDate.of(year, month, dayOfMonth)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
    }
}
