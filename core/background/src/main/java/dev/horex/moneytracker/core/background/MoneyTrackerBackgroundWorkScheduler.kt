package dev.horex.moneytracker.core.background

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.Operation
import androidx.work.WorkManager

class MoneyTrackerBackgroundWorkScheduler(
    private val workManager: WorkManager,
    private val requestFactory: MoneyTrackerWorkRequestFactory = MoneyTrackerWorkRequestFactory(),
) {
    fun enqueuePeriodic(
        spec: PeriodicBackgroundWorkSpec,
        existingWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
    ): Operation {
        return workManager.enqueueUniquePeriodicWork(
            spec.uniqueWorkName,
            existingWorkPolicy,
            requestFactory.periodicRequest(spec),
        )
    }

    fun enqueueTestRun(
        taskId: BackgroundTaskId,
        existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ): Operation {
        return workManager.enqueueUniqueWork(
            MoneyTrackerWorkRequestFactory.uniqueTestWorkName(taskId),
            existingWorkPolicy,
            requestFactory.testRequest(taskId),
        )
    }

    companion object {
        fun create(context: Context): MoneyTrackerBackgroundWorkScheduler {
            return MoneyTrackerBackgroundWorkScheduler(
                WorkManager.getInstance(context.applicationContext),
            )
        }
    }
}
