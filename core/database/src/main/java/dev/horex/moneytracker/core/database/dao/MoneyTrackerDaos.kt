package dev.horex.moneytracker.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.BudgetEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateOverrideEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateSnapshotEntity
import dev.horex.moneytracker.core.database.model.GoalTransactionEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionEntity
import dev.horex.moneytracker.core.database.model.SavingsGoalEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.model.TransactionTemplateEntity
import dev.horex.moneytracker.core.database.model.TransferEntity

data class TransactionWithRelations(
    @Embedded
    val transaction: TransactionEntity,
    @ColumnInfo(name = "category_name")
    val categoryName: String,
    @ColumnInfo(name = "category_icon")
    val categoryIcon: String,
    @ColumnInfo(name = "category_color")
    val categoryColor: String,
    @ColumnInfo(name = "account_name")
    val accountName: String,
)

data class TransferWithAccounts(
    @Embedded
    val transfer: TransferEntity,
    @ColumnInfo(name = "from_account_name")
    val fromAccountName: String,
    @ColumnInfo(name = "to_account_name")
    val toAccountName: String,
)

@Dao
interface LocalProfileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: LocalProfileEntity): Long

    @Update
    suspend fun update(profile: LocalProfileEntity)

    @Query("SELECT * FROM local_profiles ORDER BY created_at_epoch_millis ASC")
    suspend fun list(): List<LocalProfileEntity>

    @Query("SELECT * FROM local_profiles ORDER BY created_at_epoch_millis ASC LIMIT 1")
    suspend fun getFirst(): LocalProfileEntity?

    @Query("SELECT * FROM local_profiles WHERE id = :profileId")
    suspend fun getById(profileId: Long): LocalProfileEntity?

    @Delete
    suspend fun delete(profile: LocalProfileEntity)
}

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Update
    suspend fun updateAndReturnCount(account: AccountEntity): Int

    @Query("SELECT * FROM accounts WHERE profile_id = :profileId ORDER BY is_default DESC, created_at_epoch_millis ASC")
    suspend fun listByProfile(profileId: Long): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :accountId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, accountId: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE profile_id = :profileId AND is_default = 1 LIMIT 1")
    suspend fun getDefault(profileId: Long): AccountEntity?

    @Query(
        """
        UPDATE accounts
        SET is_default = 0, updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE profile_id = :profileId AND is_default = 1 AND id != :accountId
        """,
    )
    suspend fun clearOtherDefaultAccounts(profileId: Long, accountId: Long, updatedAtEpochMillis: Long)

    @Query(
        """
        UPDATE accounts
        SET is_default = 1, updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :accountId AND profile_id = :profileId
        """,
    )
    suspend fun markDefault(profileId: Long, accountId: Long, updatedAtEpochMillis: Long): Int

    @Transaction
    suspend fun setDefault(profileId: Long, accountId: Long, updatedAtEpochMillis: Long): Int {
        val updatedRows = markDefault(profileId, accountId, updatedAtEpochMillis)
        requireDefaultAccountUpdate(updatedRows)
        clearOtherDefaultAccounts(profileId, accountId, updatedAtEpochMillis)
        return updatedRows
    }

    @Query("SELECT COUNT(*) FROM accounts WHERE profile_id = :profileId")
    suspend fun countByProfile(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE profile_id = :profileId AND account_id = :accountId")
    suspend fun countTransactions(profileId: Long, accountId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM transfers
        WHERE profile_id = :profileId
          AND (from_account_id = :accountId OR to_account_id = :accountId)
        """,
    )
    suspend fun countTransfers(profileId: Long, accountId: Long): Int

    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE profile_id = :profileId AND account_id = :accountId")
    suspend fun countRecurring(profileId: Long, accountId: Long): Int

    @Query("SELECT COUNT(*) FROM transaction_templates WHERE profile_id = :profileId AND account_id = :accountId")
    suspend fun countTemplates(profileId: Long, accountId: Long): Int

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE type
                WHEN 'income' THEN amount_cents
                WHEN 'expense' THEN -amount_cents
                ELSE 0
            END
        ), 0)
        FROM transactions
        WHERE profile_id = :profileId
          AND account_id = :accountId
        """,
    )
    suspend fun getBalanceCents(profileId: Long, accountId: Long): Long

    @Query("DELETE FROM accounts WHERE id = :accountId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, accountId: Long): Int

    @Delete
    suspend fun delete(account: AccountEntity)
}

internal fun requireDefaultAccountUpdate(updatedRows: Int) {
    check(updatedRows == 1) { "Default account must exist in the target profile" }
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAndReturnCount(category: CategoryEntity): Int

    @Query(
        """
        SELECT * FROM categories
        WHERE profile_id = :profileId
          AND deleted_at_epoch_millis IS NULL
        ORDER BY name ASC
        """,
    )
    suspend fun listByProfile(profileId: Long): List<CategoryEntity>

    @Query(
        """
        SELECT * FROM categories
        WHERE profile_id = :profileId
          AND deleted_at_epoch_millis IS NULL
          AND (
              type = :type
              OR (:type IN ('expense', 'income') AND type = 'both')
          )
        ORDER BY name ASC
        """,
    )
    suspend fun listByType(profileId: Long, type: String): List<CategoryEntity>

    @Query(
        """
        SELECT * FROM categories
        WHERE profile_id = :profileId
          AND deleted_at_epoch_millis IS NULL
          AND is_protected = 0
          AND type NOT IN ('transfer', 'adjustment')
        ORDER BY name ASC
        """,
    )
    suspend fun listEditableByProfile(profileId: Long): List<CategoryEntity>

    @Query(
        """
        SELECT * FROM categories
        WHERE profile_id = :profileId
          AND deleted_at_epoch_millis IS NULL
          AND is_protected = 0
          AND type NOT IN ('transfer', 'adjustment')
          AND (
              type = :type
              OR (:type IN ('expense', 'income') AND type = 'both')
          )
        ORDER BY name ASC
        """,
    )
    suspend fun listEditableByType(profileId: Long, type: String): List<CategoryEntity>

    @Query(
        """
        SELECT * FROM categories
        WHERE profile_id = :profileId
          AND deleted_at_epoch_millis IS NULL
          AND is_protected = 0
          AND type NOT IN ('transfer', 'adjustment')
        ORDER BY name DESC
        """,
    )
    suspend fun listByProfileNameDesc(profileId: Long): List<CategoryEntity>

    @Query(
        """
        SELECT * FROM categories
        WHERE profile_id = :profileId
          AND deleted_at_epoch_millis IS NULL
          AND is_protected = 0
          AND type NOT IN ('transfer', 'adjustment')
          AND (
              type = :type
              OR (:type IN ('expense', 'income') AND type = 'both')
          )
        ORDER BY name DESC
        """,
    )
    suspend fun listByTypeNameDesc(profileId: Long, type: String): List<CategoryEntity>

    @Query(
        """
        SELECT categories.*
        FROM categories
        LEFT JOIN (
            SELECT category_id, COUNT(*) AS transaction_count
            FROM transactions
            WHERE profile_id = :profileId
              AND is_adjustment = 0
            GROUP BY category_id
        ) frequency ON frequency.category_id = categories.id
        WHERE categories.profile_id = :profileId
          AND categories.deleted_at_epoch_millis IS NULL
          AND categories.is_protected = 0
          AND categories.type NOT IN ('transfer', 'adjustment')
        ORDER BY COALESCE(frequency.transaction_count, 0) DESC, categories.name ASC
        """,
    )
    suspend fun listByFrequency(profileId: Long): List<CategoryEntity>

    @Query(
        """
        SELECT categories.*
        FROM categories
        LEFT JOIN (
            SELECT category_id, COUNT(*) AS transaction_count
            FROM transactions
            WHERE profile_id = :profileId
              AND is_adjustment = 0
            GROUP BY category_id
        ) frequency ON frequency.category_id = categories.id
        WHERE categories.profile_id = :profileId
          AND categories.deleted_at_epoch_millis IS NULL
          AND categories.is_protected = 0
          AND categories.type NOT IN ('transfer', 'adjustment')
          AND (
              categories.type = :type
              OR (:type IN ('expense', 'income') AND categories.type = 'both')
          )
        ORDER BY COALESCE(frequency.transaction_count, 0) DESC, categories.name ASC
        """,
    )
    suspend fun listByTypeFrequency(profileId: Long, type: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :categoryId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, categoryId: Long): CategoryEntity?

    @Query(
        """
        SELECT * FROM categories
        WHERE id = :categoryId
          AND profile_id = :profileId
          AND deleted_at_epoch_millis IS NULL
        """,
    )
    suspend fun getActiveById(profileId: Long, categoryId: Long): CategoryEntity?

    @Query(
        """
        SELECT * FROM categories
        WHERE profile_id = :profileId
          AND type = :type
          AND is_protected = 1
          AND deleted_at_epoch_millis IS NULL
        LIMIT 1
        """,
    )
    suspend fun getProtectedByType(profileId: Long, type: String): CategoryEntity?

    @Query(
        """
        SELECT COUNT(*) FROM categories
        WHERE profile_id = :profileId
          AND is_protected = 0
          AND type NOT IN ('transfer', 'adjustment')
          AND deleted_at_epoch_millis IS NULL
        """,
    )
    suspend fun countEditableByProfile(profileId: Long): Int

    @Query(
        """
        UPDATE categories
        SET deleted_at_epoch_millis = :deletedAtEpochMillis,
            updated_at_epoch_millis = :deletedAtEpochMillis
        WHERE id = :categoryId
          AND profile_id = :profileId
          AND deleted_at_epoch_millis IS NULL
          AND is_protected = 0
        """,
    )
    suspend fun softDelete(profileId: Long, categoryId: Long, deletedAtEpochMillis: Long): Int
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Update
    suspend fun updateAndReturnCount(transaction: TransactionEntity): Int

    @Query("SELECT * FROM transactions WHERE id = :transactionId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, transactionId: Long): TransactionEntity?

    @Query(
        """
        SELECT
            t.*,
            c.name AS category_name,
            c.icon AS category_icon,
            c.color AS category_color,
            a.name AS account_name
        FROM transactions t
        JOIN categories c ON c.id = t.category_id
        JOIN accounts a ON a.id = t.account_id
        WHERE t.id = :transactionId
          AND t.profile_id = :profileId
          AND t.is_adjustment = 0
        """,
    )
    suspend fun getVisibleWithRelations(profileId: Long, transactionId: Long): TransactionWithRelations?

    @Query(
        """
        SELECT * FROM transactions
        WHERE profile_id = :profileId AND is_adjustment = 0
        ORDER BY created_at_epoch_millis DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listHistory(profileId: Long, limit: Int, offset: Int): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE profile_id = :profileId
          AND account_id = :accountId
          AND is_adjustment = 0
        ORDER BY created_at_epoch_millis DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listByAccount(profileId: Long, accountId: Long, limit: Int, offset: Int): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE profile_id = :profileId
          AND category_id = :categoryId
          AND is_adjustment = 0
        ORDER BY created_at_epoch_millis DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listByCategory(profileId: Long, categoryId: Long, limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE profile_id = :profileId AND is_adjustment = 0")
    suspend fun countHistory(profileId: Long): Int

    @Query(
        """
        SELECT
            t.*,
            c.name AS category_name,
            c.icon AS category_icon,
            c.color AS category_color,
            a.name AS account_name
        FROM transactions t
        JOIN categories c ON c.id = t.category_id
        JOIN accounts a ON a.id = t.account_id
        WHERE t.profile_id = :profileId
          AND t.is_adjustment = 0
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND (:categoryId IS NULL OR t.category_id = :categoryId)
          AND (:fromEpochMillis IS NULL OR t.created_at_epoch_millis >= :fromEpochMillis)
          AND (:toEpochMillis IS NULL OR t.created_at_epoch_millis <= :toEpochMillis)
        ORDER BY t.created_at_epoch_millis DESC, t.id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listVisibleWithFilters(
        profileId: Long,
        accountId: Long?,
        categoryId: Long?,
        fromEpochMillis: Long?,
        toEpochMillis: Long?,
        limit: Int,
        offset: Int,
    ): List<TransactionWithRelations>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE profile_id = :profileId
          AND is_adjustment = 0
          AND (:accountId IS NULL OR account_id = :accountId)
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:fromEpochMillis IS NULL OR created_at_epoch_millis >= :fromEpochMillis)
          AND (:toEpochMillis IS NULL OR created_at_epoch_millis <= :toEpochMillis)
        """,
    )
    suspend fun countVisibleWithFilters(
        profileId: Long,
        accountId: Long?,
        categoryId: Long?,
        fromEpochMillis: Long?,
        toEpochMillis: Long?,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM transfers
        WHERE profile_id = :profileId
          AND (from_transaction_id = :transactionId OR to_transaction_id = :transactionId)
        """,
    )
    suspend fun countTransferLinks(profileId: Long, transactionId: Long): Int

    @Query("DELETE FROM transactions WHERE id = :transactionId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, transactionId: Long): Int
}

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: TransferEntity): Long

    @Query("SELECT * FROM transfers WHERE id = :transferId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, transferId: Long): TransferEntity?

    @Query(
        """
        SELECT
            t.*,
            from_accounts.name AS from_account_name,
            to_accounts.name AS to_account_name
        FROM transfers t
        JOIN accounts from_accounts ON from_accounts.id = t.from_account_id
        JOIN accounts to_accounts ON to_accounts.id = t.to_account_id
        WHERE t.id = :transferId
          AND t.profile_id = :profileId
        """,
    )
    suspend fun getWithAccounts(profileId: Long, transferId: Long): TransferWithAccounts?

    @Query(
        """
        SELECT * FROM transfers
        WHERE profile_id = :profileId
        ORDER BY created_at_epoch_millis DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listByProfile(profileId: Long, limit: Int, offset: Int): List<TransferEntity>

    @Query(
        """
        SELECT * FROM transfers
        WHERE profile_id = :profileId
          AND (from_account_id = :accountId OR to_account_id = :accountId)
        ORDER BY created_at_epoch_millis DESC
        """,
    )
    suspend fun listByAccount(profileId: Long, accountId: Long): List<TransferEntity>

    @Query(
        """
        SELECT
            t.*,
            from_accounts.name AS from_account_name,
            to_accounts.name AS to_account_name
        FROM transfers t
        JOIN accounts from_accounts ON from_accounts.id = t.from_account_id
        JOIN accounts to_accounts ON to_accounts.id = t.to_account_id
        WHERE t.profile_id = :profileId
          AND (:accountId IS NULL OR t.from_account_id = :accountId OR t.to_account_id = :accountId)
        ORDER BY t.created_at_epoch_millis DESC, t.id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listWithFilters(
        profileId: Long,
        accountId: Long?,
        limit: Int,
        offset: Int,
    ): List<TransferWithAccounts>

    @Query(
        """
        SELECT COUNT(*) FROM transfers
        WHERE profile_id = :profileId
          AND (:accountId IS NULL OR from_account_id = :accountId OR to_account_id = :accountId)
        """,
    )
    suspend fun countWithFilters(profileId: Long, accountId: Long?): Int

    @Query("DELETE FROM transfers WHERE id = :transferId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, transferId: Long): Int
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE profile_id = :profileId ORDER BY created_at_epoch_millis DESC")
    suspend fun listByProfile(profileId: Long): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :budgetId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, budgetId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE profile_id = :profileId AND category_id = :categoryId AND period = :period")
    suspend fun getByCategoryPeriod(profileId: Long, categoryId: Long, period: String): BudgetEntity?

    @Query("DELETE FROM budgets WHERE id = :budgetId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, budgetId: Long)
}

@Dao
interface RecurringTransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(recurring: RecurringTransactionEntity): Long

    @Update
    suspend fun update(recurring: RecurringTransactionEntity)

    @Query("SELECT * FROM recurring_transactions WHERE profile_id = :profileId ORDER BY created_at_epoch_millis DESC")
    suspend fun listByProfile(profileId: Long): List<RecurringTransactionEntity>

    @Query(
        """
        SELECT * FROM recurring_transactions
        WHERE is_active = 1 AND next_run_at_epoch_millis <= :nowEpochMillis
        ORDER BY next_run_at_epoch_millis ASC
        LIMIT :limit
        """,
    )
    suspend fun listDue(nowEpochMillis: Long, limit: Int): List<RecurringTransactionEntity>

    @Query("DELETE FROM recurring_transactions WHERE id = :recurringId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, recurringId: Long)
}

@Dao
interface SavingsGoalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Query("SELECT * FROM savings_goals WHERE profile_id = :profileId ORDER BY created_at_epoch_millis DESC")
    suspend fun listByProfile(profileId: Long): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :goalId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, goalId: Long): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goals WHERE profile_id = :profileId AND account_id = :accountId")
    suspend fun listByAccount(profileId: Long, accountId: Long): List<SavingsGoalEntity>

    @Query("DELETE FROM savings_goals WHERE id = :goalId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, goalId: Long)
}

@Dao
interface GoalTransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goalTransaction: GoalTransactionEntity): Long

    @Query(
        """
        SELECT * FROM goal_transactions
        WHERE profile_id = :profileId AND goal_id = :goalId
        ORDER BY created_at_epoch_millis DESC
        """,
    )
    suspend fun listByGoal(profileId: Long, goalId: Long): List<GoalTransactionEntity>
}

@Dao
interface ExchangeRateSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: ExchangeRateSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<ExchangeRateSnapshotEntity>): List<Long>

    @Query(
        """
        SELECT * FROM exchange_rate_snapshots
        WHERE snapshot_date = :snapshotDate
          AND base_currency = :baseCurrency
          AND target_currency = :targetCurrency
        LIMIT 1
        """,
    )
    suspend fun getByDate(baseCurrency: String, targetCurrency: String, snapshotDate: String): ExchangeRateSnapshotEntity?

    @Query(
        """
        SELECT * FROM exchange_rate_snapshots
        WHERE base_currency = :baseCurrency
          AND target_currency = :targetCurrency
          AND snapshot_date <= :snapshotDate
        ORDER BY snapshot_date DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestAtOrBefore(baseCurrency: String, targetCurrency: String, snapshotDate: String): ExchangeRateSnapshotEntity?

    @Query("SELECT DISTINCT currency_code FROM accounts WHERE profile_id = :profileId ORDER BY currency_code ASC")
    suspend fun listDistinctBaseCurrencies(profileId: Long): List<String>

    @Query("SELECT COALESCE(MAX(snapshot_date), '1970-01-01') FROM exchange_rate_snapshots")
    suspend fun getLatestSnapshotDate(): String
}

@Dao
interface ExchangeRateOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: ExchangeRateOverrideEntity): Long

    @Query(
        """
        SELECT * FROM exchange_rate_overrides
        WHERE profile_id = :profileId
          AND effective_date = :effectiveDate
          AND base_currency = :baseCurrency
          AND target_currency = :targetCurrency
        LIMIT 1
        """,
    )
    suspend fun getByDate(
        profileId: Long,
        baseCurrency: String,
        targetCurrency: String,
        effectiveDate: String,
    ): ExchangeRateOverrideEntity?

    @Query(
        """
        SELECT * FROM exchange_rate_overrides
        WHERE profile_id = :profileId
          AND base_currency = :baseCurrency
          AND target_currency = :targetCurrency
          AND effective_date <= :effectiveDate
        ORDER BY effective_date DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestAtOrBefore(
        profileId: Long,
        baseCurrency: String,
        targetCurrency: String,
        effectiveDate: String,
    ): ExchangeRateOverrideEntity?

    @Query(
        """
        SELECT * FROM exchange_rate_overrides
        WHERE profile_id = :profileId
        ORDER BY base_currency ASC, target_currency ASC, effective_date DESC
        """,
    )
    suspend fun listByProfile(profileId: Long): List<ExchangeRateOverrideEntity>

    @Query("DELETE FROM exchange_rate_overrides WHERE id = :overrideId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, overrideId: Long): Int
}

@Dao
interface TransactionTemplateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: TransactionTemplateEntity): Long

    @Update
    suspend fun update(template: TransactionTemplateEntity)

    @Query("SELECT * FROM transaction_templates WHERE profile_id = :profileId ORDER BY sort_order ASC, created_at_epoch_millis ASC")
    suspend fun listByProfile(profileId: Long): List<TransactionTemplateEntity>

    @Query("SELECT * FROM transaction_templates WHERE id = :templateId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, templateId: Long): TransactionTemplateEntity?

    @Query("DELETE FROM transaction_templates WHERE id = :templateId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, templateId: Long)
}
