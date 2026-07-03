package dev.horex.moneytracker.core.background

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackgroundTaskExecutorTest {
    @Test
    fun executeReturnsSkippedWhenNoHandlerIsRegistered() = runTest {
        val executor = BackgroundTaskExecutor(MutableBackgroundTaskRegistry())
        val result = executor.execute(runContext(taskId = BackgroundTaskId("sync")))

        assertEquals(BackgroundTaskResult.Skipped, result)
    }

    @Test
    fun executePassesIdempotencyGuardToDomainHandler() = runTest {
        val taskId = BackgroundTaskId("backup")
        val guard = RecordingIdempotencyGuard()
        var observedGuard: BackgroundTaskIdempotencyGuard? = null
        val registry = MutableBackgroundTaskRegistry(idempotencyGuard = guard)
            .register(taskId) { context ->
                observedGuard = context.idempotencyGuard
                BackgroundTaskResult.Success
            }
        val executor = BackgroundTaskExecutor(registry)

        val result = executor.execute(
            runContext(
                taskId = taskId,
                guard = executor.idempotencyGuard,
            ),
        )

        assertEquals(BackgroundTaskResult.Success, result)
        assertSame(guard, observedGuard)
    }

    private fun runContext(
        taskId: BackgroundTaskId,
        guard: BackgroundTaskIdempotencyGuard = AllowAllBackgroundTaskIdempotencyGuard,
    ): BackgroundTaskRunContext {
        return BackgroundTaskRunContext(
            taskId = taskId,
            trigger = BackgroundTaskTrigger.Test,
            attemptNumber = 0,
            startedAtEpochMillis = 123L,
            idempotencyGuard = guard,
        )
    }

    private class RecordingIdempotencyGuard : BackgroundTaskIdempotencyGuard {
        override suspend fun tryClaim(
            key: BackgroundTaskIdempotencyKey,
        ): BackgroundTaskIdempotencyClaim = BackgroundTaskIdempotencyClaim.Claimed

        override suspend fun markCompleted(key: BackgroundTaskIdempotencyKey) = Unit

        override suspend fun markRetryableFailure(
            key: BackgroundTaskIdempotencyKey,
            throwable: Throwable?,
        ) = Unit

        override suspend fun markPermanentFailure(
            key: BackgroundTaskIdempotencyKey,
            throwable: Throwable?,
        ) = Unit
    }
}
