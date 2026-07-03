package dev.horex.moneytracker.core.budgets

import android.app.PendingIntent
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationChannel
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationDeliveryResult
import dev.horex.moneytracker.core.notifications.MoneyTrackerNotificationRequest
import java.time.ZoneId
import kotlin.math.absoluteValue

data class BudgetNotificationProfile(
    val profileId: Long,
    val budgetNotificationsEnabled: Boolean,
)

fun interface BudgetNotificationProfileProvider {
    suspend fun listProfiles(): List<BudgetNotificationProfile>
}

fun interface BudgetNotificationNotifier {
    fun notify(request: MoneyTrackerNotificationRequest): MoneyTrackerNotificationDeliveryResult
}

data class BudgetThresholdNotificationRunResult(
    val profilesScanned: Int,
    val budgetsScanned: Int,
    val thresholdAlertsFound: Int,
    val thresholdStatesSaved: Int,
    val notificationsDelivered: Int,
    val notificationsSuppressed: Int,
)

class BudgetThresholdNotificationProcessor(
    private val profileProvider: BudgetNotificationProfileProvider,
    private val budgetsRepository: BudgetsRepository,
    private val notifier: BudgetNotificationNotifier,
    private val contentIntentFactory: (Budget, Int) -> PendingIntent? = { _, _ -> null },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun run(): BudgetThresholdNotificationRunResult {
        val now = clock()
        val profiles = profileProvider.listProfiles()
        var budgetsScanned = 0
        var thresholdAlertsFound = 0
        var thresholdStatesSaved = 0
        var notificationsDelivered = 0
        var notificationsSuppressed = 0

        profiles
            .filter(BudgetNotificationProfile::budgetNotificationsEnabled)
            .forEach { profile ->
                val budgets = budgetsRepository.listBudgets(profile.profileId)
                budgetsScanned += budgets.size

                budgets.forEach budgetLoop@{ budget ->
                    val periodStart = budget.period.currentRange(now, zoneId).fromEpochMillisInclusive
                    val threshold = budget.crossedAlertThresholds(periodStart).lastOrNull()
                        ?: return@budgetLoop
                    thresholdAlertsFound += 1

                    val stateSaved = budgetsRepository.recordBudgetThresholdNotification(
                        profileId = profile.profileId,
                        budgetId = budget.id,
                        thresholdPercent = threshold,
                        periodStartEpochMillis = periodStart,
                        notifiedAtEpochMillis = now,
                    )
                    if (!stateSaved) {
                        return@budgetLoop
                    }

                    thresholdStatesSaved += 1
                    when (notifier.notify(budget.toNotificationRequest(threshold, now))) {
                        MoneyTrackerNotificationDeliveryResult.Delivered -> notificationsDelivered += 1
                        MoneyTrackerNotificationDeliveryResult.PermissionRequired,
                        MoneyTrackerNotificationDeliveryResult.NotificationsDisabled -> notificationsSuppressed += 1
                    }
                }
            }

        return BudgetThresholdNotificationRunResult(
            profilesScanned = profiles.size,
            budgetsScanned = budgetsScanned,
            thresholdAlertsFound = thresholdAlertsFound,
            thresholdStatesSaved = thresholdStatesSaved,
            notificationsDelivered = notificationsDelivered,
            notificationsSuppressed = notificationsSuppressed,
        )
    }

    private fun Budget.toNotificationRequest(
        thresholdPercent: Int,
        nowEpochMillis: Long,
    ): MoneyTrackerNotificationRequest {
        return MoneyTrackerNotificationRequest(
            notificationId = stableBudgetNotificationId(),
            channel = MoneyTrackerNotificationChannel.BudgetAlerts,
            title = "$categoryName budget reached $thresholdPercent%",
            body = buildNotificationBody(thresholdPercent),
            contentIntent = contentIntentFactory(this, thresholdPercent),
            timestampEpochMillis = nowEpochMillis,
        )
    }
}

private fun Budget.buildNotificationBody(thresholdPercent: Int): String {
    val spent = formatMoney(spentCents, currencyCode)
    val limit = formatMoney(limitCents, currencyCode)
    val periodLabel = when (period) {
        BudgetPeriod.Weekly -> "weekly"
        BudgetPeriod.Monthly -> "monthly"
    }

    return "$categoryName spending is at $thresholdPercent% of the $periodLabel budget: $spent of $limit."
}

private fun Budget.stableBudgetNotificationId(): Int {
    var result = 17
    result = 31 * result + profileId.hashCode()
    result = 31 * result + id.hashCode()
    return result.absoluteValue.takeIf { it > 0 } ?: 1
}

private fun formatMoney(
    amountCents: Long,
    currencyCode: String,
): String {
    val absoluteCents = amountCents.absoluteValue
    val major = absoluteCents / 100
    val minor = absoluteCents % 100
    val sign = if (amountCents < 0) "-" else ""

    return "$sign$currencyCode $major.${minor.toString().padStart(2, '0')}"
}
