package dev.horex.moneytracker.core.background

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import java.util.concurrent.TimeUnit

data class PeriodicBackgroundWorkSpec(
    val taskId: BackgroundTaskId,
    val uniqueWorkName: String = MoneyTrackerWorkRequestFactory.uniquePeriodicWorkName(taskId),
    val repeatIntervalMinutes: Long = DEFAULT_REPEAT_INTERVAL_MINUTES,
    val flexIntervalMinutes: Long = DEFAULT_FLEX_INTERVAL_MINUTES,
    val initialDelayMinutes: Long = 0L,
    val constraints: Constraints = localOnlyBackgroundConstraints(),
) {
    init {
        require(uniqueWorkName.isNotBlank()) { "Unique work name must not be blank." }
        require(repeatIntervalMinutes >= MIN_PERIODIC_INTERVAL_MINUTES) {
            "Periodic work repeat interval must be at least $MIN_PERIODIC_INTERVAL_MINUTES minutes."
        }
        require(flexIntervalMinutes >= MIN_PERIODIC_FLEX_MINUTES) {
            "Periodic work flex interval must be at least $MIN_PERIODIC_FLEX_MINUTES minutes."
        }
        require(flexIntervalMinutes <= repeatIntervalMinutes) {
            "Periodic work flex interval must not exceed repeat interval."
        }
        require(initialDelayMinutes >= 0L) { "Initial delay must not be negative." }
        require(constraints.requiredNetworkType == NetworkType.NOT_REQUIRED) {
            "Money Tracker background work must stay local-only and must not require network."
        }
    }

    companion object {
        val MIN_PERIODIC_INTERVAL_MINUTES: Long =
            TimeUnit.MILLISECONDS.toMinutes(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS)
        val MIN_PERIODIC_FLEX_MINUTES: Long =
            TimeUnit.MILLISECONDS.toMinutes(PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS)
        const val DEFAULT_REPEAT_INTERVAL_MINUTES = 24L * 60L
        const val DEFAULT_FLEX_INTERVAL_MINUTES = 60L
    }
}

class MoneyTrackerWorkRequestFactory {
    fun periodicRequest(spec: PeriodicBackgroundWorkSpec): PeriodicWorkRequest {
        val builder = PeriodicWorkRequest.Builder(
            MoneyTrackerBackgroundWorker::class.java,
            spec.repeatIntervalMinutes,
            TimeUnit.MINUTES,
            spec.flexIntervalMinutes,
            TimeUnit.MINUTES,
        )
            .setInputData(inputDataFor(spec.taskId, BackgroundTaskTrigger.Periodic))
            .setConstraints(spec.constraints)
            .addTag(BACKGROUND_WORK_TAG)
            .addTag(taskTag(spec.taskId))
            .addTag(triggerTag(BackgroundTaskTrigger.Periodic))

        if (spec.initialDelayMinutes > 0L) {
            builder.setInitialDelay(spec.initialDelayMinutes, TimeUnit.MINUTES)
        }

        return builder.build()
    }

    fun testRequest(taskId: BackgroundTaskId): OneTimeWorkRequest {
        return OneTimeWorkRequest.Builder(MoneyTrackerBackgroundWorker::class.java)
            .setInputData(inputDataFor(taskId, BackgroundTaskTrigger.Test))
            .setConstraints(localOnlyBackgroundConstraints())
            .addTag(BACKGROUND_WORK_TAG)
            .addTag(taskTag(taskId))
            .addTag(triggerTag(BackgroundTaskTrigger.Test))
            .build()
    }

    companion object {
        const val BACKGROUND_WORK_TAG = "money_tracker_background"

        fun uniquePeriodicWorkName(taskId: BackgroundTaskId): String {
            return "money_tracker.periodic.${taskId.value}"
        }

        fun uniqueTestWorkName(taskId: BackgroundTaskId): String {
            return "money_tracker.test.${taskId.value}"
        }

        fun taskTag(taskId: BackgroundTaskId): String {
            return "task:${taskId.value}"
        }

        fun triggerTag(trigger: BackgroundTaskTrigger): String {
            return "trigger:${trigger.name.lowercase()}"
        }

        fun inputDataFor(taskId: BackgroundTaskId, trigger: BackgroundTaskTrigger): Data {
            return Data.Builder()
                .putString(MoneyTrackerBackgroundWorker.INPUT_TASK_ID, taskId.value)
                .putString(
                    MoneyTrackerBackgroundWorker.INPUT_TRIGGER,
                    when (trigger) {
                        BackgroundTaskTrigger.Periodic ->
                            MoneyTrackerBackgroundWorker.TRIGGER_PERIODIC
                        BackgroundTaskTrigger.Test -> MoneyTrackerBackgroundWorker.TRIGGER_TEST
                    },
                )
                .build()
        }
    }
}

fun localOnlyBackgroundConstraints(): Constraints {
    return Constraints.Builder()
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .build()
}
