package dev.horex.moneytracker.core.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

class MoneyTrackerBackgroundWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val executor: BackgroundTaskExecutor,
    private val clock: BackgroundClock = SystemBackgroundClock,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(INPUT_TASK_ID)
            ?.let(::createTaskIdOrNull)
            ?: return Result.failure()
        val trigger = inputData.getString(INPUT_TRIGGER)
            ?.let(::triggerFromInput)
            ?: return Result.failure()
        val context = BackgroundTaskRunContext(
            taskId = taskId,
            trigger = trigger,
            attemptNumber = runAttemptCount,
            startedAtEpochMillis = clock.nowEpochMillis(),
            idempotencyGuard = executor.idempotencyGuard,
        )

        return when (executor.execute(context)) {
            BackgroundTaskResult.Success,
            BackgroundTaskResult.Skipped -> Result.success()
            BackgroundTaskResult.Retry -> Result.retry()
            BackgroundTaskResult.Failure -> Result.failure()
        }
    }

    companion object {
        const val INPUT_TASK_ID = "money_tracker.background.task_id"
        const val INPUT_TRIGGER = "money_tracker.background.trigger"
        const val TRIGGER_PERIODIC = "periodic"
        const val TRIGGER_TEST = "test"
    }
}

class MoneyTrackerWorkerFactory(
    private val executor: BackgroundTaskExecutor,
    private val clock: BackgroundClock = SystemBackgroundClock,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName != MoneyTrackerBackgroundWorker::class.java.name) {
            return null
        }

        return MoneyTrackerBackgroundWorker(
            appContext = appContext,
            workerParameters = workerParameters,
            executor = executor,
            clock = clock,
        )
    }
}

private fun createTaskIdOrNull(value: String): BackgroundTaskId? {
    return runCatching { BackgroundTaskId(value) }.getOrNull()
}

private fun triggerFromInput(value: String): BackgroundTaskTrigger? {
    return when (value) {
        MoneyTrackerBackgroundWorker.TRIGGER_PERIODIC -> BackgroundTaskTrigger.Periodic
        MoneyTrackerBackgroundWorker.TRIGGER_TEST -> BackgroundTaskTrigger.Test
        else -> null
    }
}
