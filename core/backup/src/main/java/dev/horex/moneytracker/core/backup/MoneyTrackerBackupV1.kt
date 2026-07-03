package dev.horex.moneytracker.core.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MONEY_TRACKER_BACKUP_FORMAT = "MoneyTrackerBackup"
const val MONEY_TRACKER_BACKUP_VERSION = 1

@Serializable
data class MoneyTrackerBackup(
    @SerialName("format")
    val format: String = MONEY_TRACKER_BACKUP_FORMAT,
    @SerialName("version")
    val version: Int = MONEY_TRACKER_BACKUP_VERSION,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("profiles")
    val profiles: List<BackupProfile> = emptyList(),
    @SerialName("exchange_rate_snapshots")
    val exchangeRateSnapshots: List<BackupExchangeRateSnapshot> = emptyList(),
)

@Serializable
data class BackupProfile(
    @SerialName("ref")
    val ref: String,
    @SerialName("label")
    val label: String = "",
    @SerialName("language_code")
    val languageCode: String,
    @SerialName("display_currency_codes")
    val displayCurrencyCodes: List<String> = emptyList(),
    @SerialName("notification_preferences")
    val notificationPreferences: BackupNotificationPreferences = BackupNotificationPreferences(),
    @SerialName("ui_preferences")
    val uiPreferences: BackupUiPreferences = BackupUiPreferences(),
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @SerialName("accounts")
    val accounts: List<BackupAccount> = emptyList(),
    @SerialName("categories")
    val categories: List<BackupCategory> = emptyList(),
    @SerialName("transactions")
    val transactions: List<BackupTransaction> = emptyList(),
    @SerialName("transfers")
    val transfers: List<BackupTransfer> = emptyList(),
    @SerialName("budgets")
    val budgets: List<BackupBudget> = emptyList(),
    @SerialName("recurring_transactions")
    val recurringTransactions: List<BackupRecurringTransaction> = emptyList(),
    @SerialName("savings_goals")
    val savingsGoals: List<BackupSavingsGoal> = emptyList(),
    @SerialName("goal_transactions")
    val goalTransactions: List<BackupGoalTransaction> = emptyList(),
    @SerialName("exchange_rate_overrides")
    val exchangeRateOverrides: List<BackupExchangeRateOverride> = emptyList(),
    @SerialName("transaction_templates")
    val transactionTemplates: List<BackupTransactionTemplate> = emptyList(),
)

@Serializable
data class BackupNotificationPreferences(
    @SerialName("notify_budget_alerts")
    val notifyBudgetAlerts: Boolean = true,
    @SerialName("notify_recurring_reminders")
    val notifyRecurringReminders: Boolean = false,
    @SerialName("notify_weekly_summary")
    val notifyWeeklySummary: Boolean = false,
    @SerialName("notify_goal_milestones")
    val notifyGoalMilestones: Boolean = false,
)

@Serializable
data class BackupUiPreferences(
    @SerialName("stats_chart_style")
    val statsChartStyle: BackupStatsChartStyle = BackupStatsChartStyle.Donut,
    @SerialName("animate_numbers")
    val animateNumbers: Boolean? = null,
    @SerialName("theme")
    val theme: BackupTheme = BackupTheme.System,
    @SerialName("hide_amounts")
    val hideAmounts: Boolean = false,
)

@Serializable
data class BackupAccount(
    @SerialName("ref")
    val ref: String,
    @SerialName("name")
    val name: String,
    @SerialName("icon")
    val icon: String,
    @SerialName("color")
    val color: String,
    @SerialName("type")
    val type: BackupAccountType,
    @SerialName("currency_code")
    val currencyCode: String,
    @SerialName("is_default")
    val isDefault: Boolean,
    @SerialName("include_in_total")
    val includeInTotal: Boolean,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupCategory(
    @SerialName("ref")
    val ref: String,
    @SerialName("name")
    val name: String,
    @SerialName("localization_key")
    val localizationKey: String? = null,
    @SerialName("icon")
    val icon: String,
    @SerialName("type")
    val type: BackupCategoryType,
    @SerialName("color")
    val color: String,
    @SerialName("is_protected")
    val isProtected: Boolean,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @SerialName("deleted_at_epoch_millis")
    val deletedAtEpochMillis: Long? = null,
)

@Serializable
data class BackupTransaction(
    @SerialName("ref")
    val ref: String,
    @SerialName("type")
    val type: BackupTransactionType,
    @SerialName("amount_cents")
    val amountCents: Long,
    @SerialName("category_ref")
    val categoryRef: String,
    @SerialName("account_ref")
    val accountRef: String,
    @SerialName("note")
    val note: String = "",
    @SerialName("currency_code")
    val currencyCode: String,
    @SerialName("snapshot_date")
    val snapshotDate: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("is_adjustment")
    val isAdjustment: Boolean = false,
)

@Serializable
data class BackupTransfer(
    @SerialName("ref")
    val ref: String,
    @SerialName("from_account_ref")
    val fromAccountRef: String,
    @SerialName("to_account_ref")
    val toAccountRef: String,
    @SerialName("amount_cents")
    val amountCents: Long,
    @SerialName("from_currency_code")
    val fromCurrencyCode: String,
    @SerialName("to_currency_code")
    val toCurrencyCode: String,
    @SerialName("exchange_rate_e8")
    val exchangeRateE8: Long,
    @SerialName("note")
    val note: String = "",
    @SerialName("from_transaction_ref")
    val fromTransactionRef: String? = null,
    @SerialName("to_transaction_ref")
    val toTransactionRef: String? = null,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

@Serializable
data class BackupBudget(
    @SerialName("ref")
    val ref: String,
    @SerialName("category_ref")
    val categoryRef: String,
    @SerialName("limit_cents")
    val limitCents: Long,
    @SerialName("period")
    val period: BackupBudgetPeriod,
    @SerialName("currency_code")
    val currencyCode: String,
    @SerialName("notify_at_percent")
    val notifyAtPercent: Int,
    @SerialName("notifications_enabled")
    val notificationsEnabled: Boolean,
    @SerialName("last_notified_percent")
    val lastNotifiedPercent: Int,
    @SerialName("last_notified_at_epoch_millis")
    val lastNotifiedAtEpochMillis: Long? = null,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupRecurringTransaction(
    @SerialName("ref")
    val ref: String,
    @SerialName("account_ref")
    val accountRef: String,
    @SerialName("category_ref")
    val categoryRef: String,
    @SerialName("type")
    val type: BackupTransactionType,
    @SerialName("amount_cents")
    val amountCents: Long,
    @SerialName("currency_code")
    val currencyCode: String,
    @SerialName("note")
    val note: String = "",
    @SerialName("frequency")
    val frequency: BackupRecurringFrequency,
    @SerialName("next_run_at_epoch_millis")
    val nextRunAtEpochMillis: Long,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupSavingsGoal(
    @SerialName("ref")
    val ref: String,
    @SerialName("name")
    val name: String,
    @SerialName("target_cents")
    val targetCents: Long,
    @SerialName("current_cents")
    val currentCents: Long,
    @SerialName("currency_code")
    val currencyCode: String,
    @SerialName("deadline_date")
    val deadlineDate: String? = null,
    @SerialName("account_ref")
    val accountRef: String? = null,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupGoalTransaction(
    @SerialName("ref")
    val ref: String,
    @SerialName("goal_ref")
    val goalRef: String,
    @SerialName("type")
    val type: BackupGoalTransactionType,
    @SerialName("amount_cents")
    val amountCents: Long,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

@Serializable
data class BackupExchangeRateSnapshot(
    @SerialName("snapshot_date")
    val snapshotDate: String,
    @SerialName("base_currency_code")
    val baseCurrencyCode: String,
    @SerialName("target_currency_code")
    val targetCurrencyCode: String,
    @SerialName("rate_e8")
    val rateE8: Long,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

@Serializable
data class BackupExchangeRateOverride(
    @SerialName("ref")
    val ref: String,
    @SerialName("effective_date")
    val effectiveDate: String,
    @SerialName("base_currency_code")
    val baseCurrencyCode: String,
    @SerialName("target_currency_code")
    val targetCurrencyCode: String,
    @SerialName("rate_e8")
    val rateE8: Long,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupTransactionTemplate(
    @SerialName("ref")
    val ref: String,
    @SerialName("name")
    val name: String = "",
    @SerialName("type")
    val type: BackupTransactionType,
    @SerialName("amount_cents")
    val amountCents: Long,
    @SerialName("amount_fixed")
    val amountFixed: Boolean,
    @SerialName("category_ref")
    val categoryRef: String,
    @SerialName("account_ref")
    val accountRef: String,
    @SerialName("currency_code")
    val currencyCode: String,
    @SerialName("note")
    val note: String = "",
    @SerialName("sort_order")
    val sortOrder: Int,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Serializable
enum class BackupAccountType {
    @SerialName("checking")
    Checking,

    @SerialName("savings")
    Savings,

    @SerialName("cash")
    Cash,

    @SerialName("credit")
    Credit,

    @SerialName("crypto")
    Crypto,
}

@Serializable
enum class BackupCategoryType {
    @SerialName("expense")
    Expense,

    @SerialName("income")
    Income,

    @SerialName("both")
    Both,

    @SerialName("savings")
    Savings,

    @SerialName("transfer")
    Transfer,

    @SerialName("adjustment")
    Adjustment,
}

@Serializable
enum class BackupTransactionType {
    @SerialName("expense")
    Expense,

    @SerialName("income")
    Income,
}

@Serializable
enum class BackupBudgetPeriod {
    @SerialName("weekly")
    Weekly,

    @SerialName("monthly")
    Monthly,
}

@Serializable
enum class BackupRecurringFrequency {
    @SerialName("daily")
    Daily,

    @SerialName("weekly")
    Weekly,

    @SerialName("monthly")
    Monthly,

    @SerialName("yearly")
    Yearly,
}

@Serializable
enum class BackupGoalTransactionType {
    @SerialName("deposit")
    Deposit,

    @SerialName("withdraw")
    Withdraw,
}

@Serializable
enum class BackupStatsChartStyle {
    @SerialName("donut")
    Donut,

    @SerialName("stacked_bar")
    StackedBar,

    @SerialName("dual_bar")
    DualBar,

    @SerialName("profit_bars")
    ProfitBars,
}

@Serializable
enum class BackupTheme {
    @SerialName("system")
    System,

    @SerialName("light")
    Light,

    @SerialName("dark")
    Dark,
}
