package dev.horex.moneytracker.core.background

@JvmInline
value class BackgroundTaskId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Background task id must not be blank." }
        require(value == value.trim()) { "Background task id must not have surrounding whitespace." }
        require(value.all(::isAllowedTaskIdCharacter)) {
            "Background task id must contain only lowercase letters, digits, '.', '_' or '-'."
        }
    }

    override fun toString(): String = value
}

enum class BackgroundTaskTrigger {
    Periodic,
    Test,
}

enum class BackgroundTaskResult {
    Success,
    Retry,
    Failure,
    Skipped,
}

data class BackgroundTaskIdempotencyKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Idempotency key must not be blank." }
        require(value == value.trim()) { "Idempotency key must not have surrounding whitespace." }
    }
}

enum class BackgroundTaskIdempotencyClaim {
    Claimed,
    AlreadyCompleted,
    AlreadyRunning,
}

interface BackgroundTaskIdempotencyGuard {
    suspend fun tryClaim(key: BackgroundTaskIdempotencyKey): BackgroundTaskIdempotencyClaim

    suspend fun markCompleted(key: BackgroundTaskIdempotencyKey)

    suspend fun markRetryableFailure(key: BackgroundTaskIdempotencyKey, throwable: Throwable?)

    suspend fun markPermanentFailure(key: BackgroundTaskIdempotencyKey, throwable: Throwable?)
}

object AllowAllBackgroundTaskIdempotencyGuard : BackgroundTaskIdempotencyGuard {
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

data class BackgroundTaskRunContext(
    val taskId: BackgroundTaskId,
    val trigger: BackgroundTaskTrigger,
    val attemptNumber: Int,
    val startedAtEpochMillis: Long,
    val idempotencyGuard: BackgroundTaskIdempotencyGuard,
) {
    init {
        require(attemptNumber >= 0) { "Attempt number must not be negative." }
        require(startedAtEpochMillis >= 0L) { "Start time must not be negative." }
    }
}

fun interface BackgroundTaskHandler {
    suspend fun run(context: BackgroundTaskRunContext): BackgroundTaskResult
}

fun interface BackgroundClock {
    fun nowEpochMillis(): Long
}

object SystemBackgroundClock : BackgroundClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

private fun isAllowedTaskIdCharacter(character: Char): Boolean {
    return character in 'a'..'z' ||
        character in '0'..'9' ||
        character == '.' ||
        character == '_' ||
        character == '-'
}
