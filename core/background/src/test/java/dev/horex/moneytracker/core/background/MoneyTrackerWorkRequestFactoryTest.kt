package dev.horex.moneytracker.core.background

import androidx.work.Constraints
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerWorkRequestFactoryTest {
    @Test
    fun periodicSpecDefaultsAreLocalOnlyAndValidForWorkManager() {
        val spec = PeriodicBackgroundWorkSpec(taskId = BackgroundTaskId("backup"))

        assertEquals(NetworkType.NOT_REQUIRED, spec.constraints.requiredNetworkType)
        assertTrue(
            spec.repeatIntervalMinutes >=
                PeriodicBackgroundWorkSpec.MIN_PERIODIC_INTERVAL_MINUTES,
        )
        assertTrue(
            spec.flexIntervalMinutes >=
                PeriodicBackgroundWorkSpec.MIN_PERIODIC_FLEX_MINUTES,
        )
    }

    @Test
    fun periodicSpecRejectsNetworkConstraint() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        assertThrows(IllegalArgumentException::class.java) {
            PeriodicBackgroundWorkSpec(
                taskId = BackgroundTaskId("sync"),
                constraints = constraints,
            )
        }
    }

    @Test
    fun periodicSpecRejectsIntervalsThatWorkManagerCannotSchedule() {
        assertThrows(IllegalArgumentException::class.java) {
            PeriodicBackgroundWorkSpec(
                taskId = BackgroundTaskId("backup"),
                repeatIntervalMinutes =
                    PeriodicBackgroundWorkSpec.MIN_PERIODIC_INTERVAL_MINUTES - 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PeriodicBackgroundWorkSpec(
                taskId = BackgroundTaskId("backup"),
                flexIntervalMinutes =
                    PeriodicBackgroundWorkSpec.MIN_PERIODIC_FLEX_MINUTES - 1,
            )
        }
    }

    @Test
    fun workRequestsExposeStableTagsForTestHooks() {
        val taskId = BackgroundTaskId("notifications")
        val factory = MoneyTrackerWorkRequestFactory()
        val periodicRequest = factory.periodicRequest(PeriodicBackgroundWorkSpec(taskId = taskId))
        val testRequest = factory.testRequest(taskId)

        assertTrue(MoneyTrackerWorkRequestFactory.BACKGROUND_WORK_TAG in periodicRequest.tags)
        assertTrue(MoneyTrackerWorkRequestFactory.taskTag(taskId) in periodicRequest.tags)
        assertTrue(
            MoneyTrackerWorkRequestFactory.triggerTag(BackgroundTaskTrigger.Periodic) in
                periodicRequest.tags,
        )
        assertTrue(MoneyTrackerWorkRequestFactory.BACKGROUND_WORK_TAG in testRequest.tags)
        assertTrue(MoneyTrackerWorkRequestFactory.taskTag(taskId) in testRequest.tags)
        assertTrue(
            MoneyTrackerWorkRequestFactory.triggerTag(BackgroundTaskTrigger.Test) in
                testRequest.tags,
        )
    }

    @Test
    fun inputDataMatchesWorkerContract() {
        val data = MoneyTrackerWorkRequestFactory.inputDataFor(
            taskId = BackgroundTaskId("backup"),
            trigger = BackgroundTaskTrigger.Periodic,
        )

        assertEquals("backup", data.getString(MoneyTrackerBackgroundWorker.INPUT_TASK_ID))
        assertEquals(
            MoneyTrackerBackgroundWorker.TRIGGER_PERIODIC,
            data.getString(MoneyTrackerBackgroundWorker.INPUT_TRIGGER),
        )
    }
}
