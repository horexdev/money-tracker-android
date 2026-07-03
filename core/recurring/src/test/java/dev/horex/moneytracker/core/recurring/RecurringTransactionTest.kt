package dev.horex.moneytracker.core.recurring

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecurringTransactionTest {
    @Test
    fun nextRunAfterAddsExpectedCalendarIntervals() {
        val base = utcDateTime(2024, 3, 15, 10, 0)

        assertEquals(
            utcDateTime(2024, 3, 16, 10, 0),
            RecurringFrequency.Daily.nextRunAfter(base, ZoneOffset.UTC),
        )
        assertEquals(
            utcDateTime(2024, 3, 22, 10, 0),
            RecurringFrequency.Weekly.nextRunAfter(base, ZoneOffset.UTC),
        )
        assertEquals(
            utcDateTime(2024, 4, 15, 10, 0),
            RecurringFrequency.Monthly.nextRunAfter(base, ZoneOffset.UTC),
        )
        assertEquals(
            utcDateTime(2025, 3, 15, 10, 0),
            RecurringFrequency.Yearly.nextRunAfter(base, ZoneOffset.UTC),
        )
    }

    @Test
    fun nextRunAfterKeepsSourceMonthEndOverflowBehavior() {
        val jan31NonLeap = utcDateTime(2023, 1, 31, 0, 0)
        val jan31Leap = utcDateTime(2024, 1, 31, 0, 0)
        val feb29Leap = utcDateTime(2024, 2, 29, 0, 0)

        assertEquals(
            utcDateTime(2023, 3, 3, 0, 0),
            RecurringFrequency.Monthly.nextRunAfter(jan31NonLeap, ZoneOffset.UTC),
        )
        assertEquals(
            utcDateTime(2024, 3, 2, 0, 0),
            RecurringFrequency.Monthly.nextRunAfter(jan31Leap, ZoneOffset.UTC),
        )
        assertEquals(
            utcDateTime(2025, 3, 1, 0, 0),
            RecurringFrequency.Yearly.nextRunAfter(feb29Leap, ZoneOffset.UTC),
        )
    }

    @Test
    fun frequencyParsingRejectsUnknownValues() {
        assertEquals(RecurringFrequency.Monthly, RecurringFrequency.fromStorageValue("monthly"))
        assertThrows(InvalidRecurringFrequencyException::class.java) {
            RecurringFrequency.fromStorageValue("biweekly")
        }
    }

    private fun utcDateTime(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
    ): Long {
        return LocalDateTime.of(year, month, dayOfMonth, hour, minute)
            .atZone(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }
}
