package dev.horex.moneytracker.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object MoneyTrackerTables {
    const val LOCAL_PROFILES = "local_profiles"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val TRANSACTIONS = "transactions"
    const val TRANSFERS = "transfers"
    const val BUDGETS = "budgets"
    const val RECURRING_TRANSACTIONS = "recurring_transactions"
    const val RECURRING_TRANSACTION_RUNS = "recurring_transaction_runs"
    const val SAVINGS_GOALS = "savings_goals"
    const val GOAL_TRANSACTIONS = "goal_transactions"
    const val EXCHANGE_RATE_SNAPSHOTS = "exchange_rate_snapshots"
    const val EXCHANGE_RATE_OVERRIDES = "exchange_rate_overrides"
    const val TRANSACTION_TEMPLATES = "transaction_templates"
}

@Entity(tableName = MoneyTrackerTables.LOCAL_PROFILES)
data class LocalProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "label", defaultValue = "''")
    val label: String = "",
    @ColumnInfo(name = "language_code", defaultValue = "'en'")
    val languageCode: String = "en",
    @ColumnInfo(name = "display_currencies_csv", defaultValue = "''")
    val displayCurrenciesCsv: String = "",
    @ColumnInfo(name = "notify_budget_alerts", defaultValue = "1")
    val notifyBudgetAlerts: Boolean = true,
    @ColumnInfo(name = "notify_recurring_reminders", defaultValue = "0")
    val notifyRecurringReminders: Boolean = false,
    @ColumnInfo(name = "notify_weekly_summary", defaultValue = "0")
    val notifyWeeklySummary: Boolean = false,
    @ColumnInfo(name = "notify_goal_milestones", defaultValue = "0")
    val notifyGoalMilestones: Boolean = false,
    @ColumnInfo(name = "stats_chart_style", defaultValue = "'donut'")
    val statsChartStyle: String = "donut",
    @ColumnInfo(name = "animate_numbers")
    val animateNumbers: Boolean? = null,
    @ColumnInfo(name = "theme", defaultValue = "'system'")
    val theme: String = "system",
    @ColumnInfo(name = "hide_amounts", defaultValue = "0")
    val hideAmounts: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.ACCOUNTS,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["profile_id", "name"], unique = true),
        Index(value = ["profile_id", "is_default"]),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "icon", defaultValue = "'wallet'")
    val icon: String = "wallet",
    @ColumnInfo(name = "color", defaultValue = "'#6366f1'")
    val color: String = "#6366f1",
    @ColumnInfo(name = "type", defaultValue = "'checking'")
    val type: String = "checking",
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "is_default", defaultValue = "0")
    val isDefault: Boolean = false,
    @ColumnInfo(name = "include_in_total", defaultValue = "1")
    val includeInTotal: Boolean = true,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.CATEGORIES,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["profile_id", "name"], unique = true),
        Index(value = ["profile_id", "type"]),
        Index(value = ["profile_id", "deleted_at_epoch_millis"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "icon", defaultValue = "'star'")
    val icon: String = "star",
    @ColumnInfo(name = "type", defaultValue = "'both'")
    val type: String = "both",
    @ColumnInfo(name = "color", defaultValue = "'#6366f1'")
    val color: String = "#6366f1",
    @ColumnInfo(name = "is_protected", defaultValue = "0")
    val isProtected: Boolean = false,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0,
    @ColumnInfo(name = "deleted_at_epoch_millis")
    val deletedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = MoneyTrackerTables.TRANSACTIONS,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["profile_id", "created_at_epoch_millis"]),
        Index(value = ["profile_id", "is_adjustment", "created_at_epoch_millis", "id"]),
        Index(value = ["profile_id", "account_id", "created_at_epoch_millis"]),
        Index(value = ["profile_id", "account_id", "is_adjustment", "created_at_epoch_millis", "id"]),
        Index(value = ["profile_id", "category_id", "created_at_epoch_millis"]),
        Index(value = ["profile_id", "category_id", "is_adjustment", "created_at_epoch_millis", "id"]),
        Index(value = ["profile_id", "type", "is_adjustment", "created_at_epoch_millis", "id"]),
        Index(value = ["profile_id", "currency_code", "is_adjustment", "created_at_epoch_millis", "id"]),
        Index(value = ["account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["snapshot_date"]),
        Index(value = ["is_adjustment"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "note", defaultValue = "''")
    val note: String = "",
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "snapshot_date")
    val snapshotDate: String,
    @ColumnInfo(name = "is_adjustment", defaultValue = "0")
    val isAdjustment: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.TRANSFERS,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_transaction_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_transaction_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["profile_id", "created_at_epoch_millis"]),
        Index(value = ["from_account_id"]),
        Index(value = ["to_account_id"]),
        Index(value = ["from_transaction_id"]),
        Index(value = ["to_transaction_id"]),
    ],
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "from_account_id")
    val fromAccountId: Long,
    @ColumnInfo(name = "to_account_id")
    val toAccountId: Long,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "from_currency_code")
    val fromCurrencyCode: String,
    @ColumnInfo(name = "to_currency_code")
    val toCurrencyCode: String,
    @ColumnInfo(name = "exchange_rate_e8", defaultValue = "100000000")
    val exchangeRateE8: Long = 100_000_000,
    @ColumnInfo(name = "note", defaultValue = "''")
    val note: String = "",
    @ColumnInfo(name = "from_transaction_id")
    val fromTransactionId: Long? = null,
    @ColumnInfo(name = "to_transaction_id")
    val toTransactionId: Long? = null,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.BUDGETS,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["category_id"]),
        Index(value = ["profile_id", "category_id", "period"], unique = true),
    ],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "limit_cents")
    val limitCents: Long,
    @ColumnInfo(name = "period")
    val period: String,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "notify_at_percent", defaultValue = "80")
    val notifyAtPercent: Int = 80,
    @ColumnInfo(name = "notifications_enabled", defaultValue = "1")
    val notificationsEnabled: Boolean = true,
    @ColumnInfo(name = "last_notified_percent", defaultValue = "0")
    val lastNotifiedPercent: Int = 0,
    @ColumnInfo(name = "last_notified_at_epoch_millis")
    val lastNotifiedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.RECURRING_TRANSACTIONS,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["profile_id", "is_active", "next_run_at_epoch_millis"]),
        Index(value = ["is_active", "next_run_at_epoch_millis"]),
    ],
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "note", defaultValue = "''")
    val note: String = "",
    @ColumnInfo(name = "frequency")
    val frequency: String,
    @ColumnInfo(name = "next_run_at_epoch_millis")
    val nextRunAtEpochMillis: Long,
    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.RECURRING_TRANSACTION_RUNS,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecurringTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurring_transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["recurring_transaction_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["profile_id", "recurring_transaction_id", "scheduled_for_epoch_millis"], unique = true),
    ],
)
data class RecurringTransactionRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "recurring_transaction_id")
    val recurringTransactionId: Long,
    @ColumnInfo(name = "scheduled_for_epoch_millis")
    val scheduledForEpochMillis: Long,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,
    @ColumnInfo(name = "processed_at_epoch_millis")
    val processedAtEpochMillis: Long,
)

@Entity(
    tableName = MoneyTrackerTables.SAVINGS_GOALS,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["account_id"]),
        Index(value = ["profile_id", "created_at_epoch_millis"]),
    ],
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "target_cents")
    val targetCents: Long,
    @ColumnInfo(name = "current_cents", defaultValue = "0")
    val currentCents: Long = 0,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "deadline_date")
    val deadlineDate: String? = null,
    @ColumnInfo(name = "account_id")
    val accountId: Long? = null,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.GOAL_TRANSACTIONS,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["goal_id"]),
        Index(value = ["profile_id", "created_at_epoch_millis"]),
    ],
)
data class GoalTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "goal_id")
    val goalId: Long,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.EXCHANGE_RATE_SNAPSHOTS,
    indices = [
        Index(value = ["snapshot_date", "base_currency", "target_currency"], unique = true),
        Index(value = ["snapshot_date", "base_currency"]),
        Index(value = ["base_currency", "target_currency", "snapshot_date"]),
    ],
)
data class ExchangeRateSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "snapshot_date")
    val snapshotDate: String,
    @ColumnInfo(name = "base_currency")
    val baseCurrency: String,
    @ColumnInfo(name = "target_currency")
    val targetCurrency: String,
    @ColumnInfo(name = "rate_e8")
    val rateE8: Long,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.EXCHANGE_RATE_OVERRIDES,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["profile_id", "effective_date", "base_currency", "target_currency"], unique = true),
        Index(value = ["profile_id", "base_currency", "target_currency", "effective_date"]),
    ],
)
data class ExchangeRateOverrideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "effective_date")
    val effectiveDate: String,
    @ColumnInfo(name = "base_currency")
    val baseCurrency: String,
    @ColumnInfo(name = "target_currency")
    val targetCurrency: String,
    @ColumnInfo(name = "rate_e8")
    val rateE8: Long,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0,
)

@Entity(
    tableName = MoneyTrackerTables.TRANSACTION_TEMPLATES,
    foreignKeys = [
        ForeignKey(
            entity = LocalProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["profile_id", "sort_order"]),
    ],
)
data class TransactionTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "name", defaultValue = "''")
    val name: String = "",
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "amount_fixed", defaultValue = "1")
    val amountFixed: Boolean = true,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "note", defaultValue = "''")
    val note: String = "",
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0,
)
