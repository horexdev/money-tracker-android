package dev.horex.moneytracker.core.budgets

import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationDeliveryResult
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationRequest
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetThresholdNotificationProcessorTest {
    @Test
    fun runDeliversHighestCrossedThresholdAndSavesState() = runTest {
        val repository = FakeBudgetsRepository(
            budget(
                limitCents = 10_000,
                spentCents = 9_600,
            ),
        )
        val notifier = RecordingNotifier()
        val processor = processor(repository, notifier)

        val result = processor.run()

        assertEquals(1, result.profilesScanned)
        assertEquals(1, result.budgetsScanned)
        assertEquals(1, result.thresholdAlertsFound)
        assertEquals(1, result.thresholdStatesSaved)
        assertEquals(1, result.notificationsDelivered)
        assertEquals(95, repository.singleBudget().lastNotifiedPercent)
        assertEquals(JULY_15, repository.singleBudget().lastNotifiedAtEpochMillis)
        assertEquals("Food budget reached 95%", notifier.requests.single().title)
        assertTrue(notifier.requests.single().body.contains("USD 96.00 of USD 100.00"))
    }

    @Test
    fun runDoesNotDuplicateAlreadySavedThresholdForCurrentPeriod() = runTest {
        val repository = FakeBudgetsRepository(
            budget(
                limitCents = 10_000,
                spentCents = 9_600,
                lastNotifiedPercent = 95,
                lastNotifiedAtEpochMillis = JULY_10,
            ),
        )
        val notifier = RecordingNotifier()
        val processor = processor(repository, notifier)

        val result = processor.run()

        assertEquals(0, result.thresholdAlertsFound)
        assertEquals(0, result.thresholdStatesSaved)
        assertEquals(0, result.notificationsDelivered)
        assertEquals(emptyList<MoneyTrackerNotificationRequest>(), notifier.requests)
    }

    @Test
    fun runTreatsPreviousPeriodNotificationAsResetState() = runTest {
        val repository = FakeBudgetsRepository(
            budget(
                limitCents = 10_000,
                spentCents = 7_500,
                lastNotifiedPercent = 95,
                lastNotifiedAtEpochMillis = JUNE_30,
            ),
        )
        val notifier = RecordingNotifier()
        val processor = processor(repository, notifier)

        val result = processor.run()

        assertEquals(1, result.thresholdAlertsFound)
        assertEquals(1, result.thresholdStatesSaved)
        assertEquals(1, result.notificationsDelivered)
        assertEquals(75, repository.singleBudget().lastNotifiedPercent)
        assertEquals(JULY_15, repository.singleBudget().lastNotifiedAtEpochMillis)
        assertEquals("Food budget reached 75%", notifier.requests.single().title)
    }

    @Test
    fun runSkipsProfilesAndBudgetsWithNotificationsDisabled() = runTest {
        val repository = FakeBudgetsRepository(
            budget(
                limitCents = 10_000,
                spentCents = 12_000,
                notificationsEnabled = false,
            ),
        )
        val notifier = RecordingNotifier()
        val processor = BudgetThresholdNotificationProcessor(
            profileProvider = BudgetNotificationProfileProvider {
                listOf(
                    BudgetNotificationProfile(
                        profileId = PROFILE_ID,
                        budgetNotificationsEnabled = false,
                    ),
                )
            },
            budgetsRepository = repository,
            notifier = notifier,
            clock = { JULY_15 },
            zoneId = ZoneOffset.UTC,
        )

        val result = processor.run()

        assertEquals(1, result.profilesScanned)
        assertEquals(0, result.budgetsScanned)
        assertEquals(0, result.thresholdStatesSaved)
        assertEquals(emptyList<MoneyTrackerNotificationRequest>(), notifier.requests)
    }

    @Test
    fun runKeepsSavedStateWhenSystemNotificationDeliveryIsSuppressed() = runTest {
        val repository = FakeBudgetsRepository(
            budget(
                limitCents = 10_000,
                spentCents = 5_000,
            ),
        )
        val notifier = RecordingNotifier(MoneyTrackerNotificationDeliveryResult.PermissionRequired)
        val processor = processor(repository, notifier)

        val result = processor.run()

        assertEquals(1, result.thresholdStatesSaved)
        assertEquals(0, result.notificationsDelivered)
        assertEquals(1, result.notificationsSuppressed)
        assertEquals(50, repository.singleBudget().lastNotifiedPercent)
        assertEquals(1, notifier.requests.size)
    }

    private fun processor(
        repository: FakeBudgetsRepository,
        notifier: RecordingNotifier,
    ): BudgetThresholdNotificationProcessor {
        return BudgetThresholdNotificationProcessor(
            profileProvider = BudgetNotificationProfileProvider {
                listOf(
                    BudgetNotificationProfile(
                        profileId = PROFILE_ID,
                        budgetNotificationsEnabled = true,
                    ),
                )
            },
            budgetsRepository = repository,
            notifier = notifier,
            clock = { JULY_15 },
            zoneId = ZoneOffset.UTC,
        )
    }

    private class RecordingNotifier(
        private val result: MoneyTrackerNotificationDeliveryResult =
            MoneyTrackerNotificationDeliveryResult.Delivered,
    ) : BudgetNotificationNotifier {
        val requests = mutableListOf<MoneyTrackerNotificationRequest>()

        override fun notify(request: MoneyTrackerNotificationRequest): MoneyTrackerNotificationDeliveryResult {
            requests += request
            return result
        }
    }

    private class FakeBudgetsRepository(
        initialBudget: Budget,
    ) : BudgetsRepository {
        private var budget = initialBudget

        fun singleBudget(): Budget = budget

        override suspend fun listBudgets(profileId: Long): List<Budget> {
            return listOf(budget).filter { it.profileId == profileId }
        }

        override suspend fun getBudget(profileId: Long, budgetId: Long): Budget {
            return listBudgets(profileId).single { it.id == budgetId }
        }

        override suspend fun createBudget(profileId: Long, input: CreateBudgetInput): Budget {
            throw UnsupportedOperationException()
        }

        override suspend fun updateBudget(
            profileId: Long,
            budgetId: Long,
            input: UpdateBudgetInput,
        ): Budget {
            throw UnsupportedOperationException()
        }

        override suspend fun deleteBudget(profileId: Long, budgetId: Long) {
            throw UnsupportedOperationException()
        }

        override suspend fun listBudgetTransactions(
            profileId: Long,
            budgetId: Long,
        ): List<BudgetTransaction> {
            throw UnsupportedOperationException()
        }

        override suspend fun recordBudgetThresholdNotification(
            profileId: Long,
            budgetId: Long,
            thresholdPercent: Int,
            periodStartEpochMillis: Long,
            notifiedAtEpochMillis: Long,
        ): Boolean {
            if (budget.profileId != profileId || budget.id != budgetId || !budget.notificationsEnabled) {
                return false
            }

            val alreadyNotified = budget.lastNotifiedAtEpochMillis != null &&
                budget.lastNotifiedAtEpochMillis!! >= periodStartEpochMillis &&
                budget.lastNotifiedPercent >= thresholdPercent
            if (alreadyNotified) {
                return false
            }

            budget = budget.copy(
                lastNotifiedPercent = thresholdPercent,
                lastNotifiedAtEpochMillis = notifiedAtEpochMillis,
            )
            return true
        }
    }

    private fun budget(
        limitCents: Long,
        spentCents: Long,
        notificationsEnabled: Boolean = true,
        lastNotifiedPercent: Int = 0,
        lastNotifiedAtEpochMillis: Long? = null,
    ): Budget {
        return Budget(
            id = BUDGET_ID,
            profileId = PROFILE_ID,
            categoryId = 2L,
            categoryName = "Food",
            categoryIcon = "fork",
            categoryColor = "#64748b",
            limitCents = limitCents,
            spentCents = spentCents,
            period = BudgetPeriod.Monthly,
            currencyCode = "USD",
            notifyAtPercent = DEFAULT_BUDGET_NOTIFY_AT_PERCENT,
            notificationsEnabled = notificationsEnabled,
            lastNotifiedPercent = lastNotifiedPercent,
            lastNotifiedAtEpochMillis = lastNotifiedAtEpochMillis,
            createdAtEpochMillis = JULY_01,
            updatedAtEpochMillis = JULY_01,
        )
    }

    private companion object {
        const val PROFILE_ID = 1L
        const val BUDGET_ID = 7L

        val JUNE_30 = utcDate(2026, 6, 30)
        val JULY_01 = utcDate(2026, 7, 1)
        val JULY_10 = utcDate(2026, 7, 10)
        val JULY_15 = utcDate(2026, 7, 15)

        fun utcDate(year: Int, month: Int, dayOfMonth: Int): Long {
            return LocalDate.of(year, month, dayOfMonth)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
    }
}
