package dev.horex.moneytracker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerAppContainerInitializationTest {
    @Test
    fun declaresBackgroundTaskRegistryBeforeInitRegistration() {
        val source = readContainerSource()

        assertPositionBefore(
            source = source,
            before = "val backgroundTaskRegistry",
            after = "init {\n        registerBackgroundTasks()",
            message = "backgroundTaskRegistry must be declared before init uses it.",
        )
    }

    @Test
    fun declaresWorkerFactoryBeforeSchedulingEntryPoint() {
        val source = readContainerSource()

        assertPositionBefore(
            source = source,
            before = "val backgroundWorkerFactory",
            after = "fun startBackgroundWork()",
            message = "backgroundWorkerFactory must be available before background work can be scheduled.",
        )
    }

    private fun readContainerSource(): String =
        File("src/main/java/dev/horex/moneytracker/MoneyTrackerAppContainer.kt").readText()

    private fun assertPositionBefore(
        source: String,
        before: String,
        after: String,
        message: String,
    ) {
        val beforeIndex = source.indexOf(before)
        val afterIndex = source.indexOf(after)

        assertTrue("$message Missing marker: $before", beforeIndex >= 0)
        assertTrue("$message Missing marker: $after", afterIndex >= 0)
        assertTrue(message, beforeIndex < afterIndex)
    }
}
