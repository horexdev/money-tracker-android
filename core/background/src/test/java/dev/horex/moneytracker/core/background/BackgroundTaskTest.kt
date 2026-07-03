package dev.horex.moneytracker.core.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackgroundTaskTest {
    @Test
    fun taskIdRejectsBlankWhitespaceAndUnsafeCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundTaskId(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundTaskId(" backup")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundTaskId("BackUp")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundTaskId("backup/sync")
        }
    }

    @Test
    fun taskIdAllowsStableLocalIdentifiers() {
        assertEquals("backup.daily_1", BackgroundTaskId("backup.daily_1").value)
        assertEquals("sync-state", BackgroundTaskId("sync-state").value)
    }

    @Test
    fun idempotencyKeyRequiresStableNonBlankValue() {
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundTaskIdempotencyKey("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundTaskIdempotencyKey(" daily-summary ")
        }

        assertEquals(
            "daily-summary:2026-07-03",
            BackgroundTaskIdempotencyKey("daily-summary:2026-07-03").value,
        )
    }
}
