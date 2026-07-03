package dev.horex.moneytracker.core.background

interface BackgroundTaskRegistry {
    val idempotencyGuard: BackgroundTaskIdempotencyGuard

    fun handlerFor(taskId: BackgroundTaskId): BackgroundTaskHandler?
}

class MutableBackgroundTaskRegistry(
    override val idempotencyGuard: BackgroundTaskIdempotencyGuard =
        AllowAllBackgroundTaskIdempotencyGuard,
) : BackgroundTaskRegistry {
    private val handlers = linkedMapOf<BackgroundTaskId, BackgroundTaskHandler>()

    val registeredTaskIds: Set<BackgroundTaskId>
        @Synchronized
        get() = handlers.keys.toSet()

    @Synchronized
    fun register(
        taskId: BackgroundTaskId,
        handler: BackgroundTaskHandler,
    ): MutableBackgroundTaskRegistry {
        handlers[taskId] = handler
        return this
    }

    @Synchronized
    fun unregister(taskId: BackgroundTaskId): Boolean {
        return handlers.remove(taskId) != null
    }

    @Synchronized
    override fun handlerFor(taskId: BackgroundTaskId): BackgroundTaskHandler? {
        return handlers[taskId]
    }
}

class BackgroundTaskExecutor(
    private val registry: BackgroundTaskRegistry,
) {
    val idempotencyGuard: BackgroundTaskIdempotencyGuard
        get() = registry.idempotencyGuard

    suspend fun execute(context: BackgroundTaskRunContext): BackgroundTaskResult {
        val handler = registry.handlerFor(context.taskId) ?: return BackgroundTaskResult.Skipped
        return handler.run(context)
    }
}
