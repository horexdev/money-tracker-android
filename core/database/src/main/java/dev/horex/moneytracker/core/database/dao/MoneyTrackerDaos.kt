package dev.horex.moneytracker.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.BudgetEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateSnapshotEntity
import dev.horex.moneytracker.core.database.model.GoalTransactionEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionEntity
import dev.horex.moneytracker.core.database.model.SavingsGoalEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.model.TransactionTemplateEntity
import dev.horex.moneytracker.core.database.model.TransferEntity

@Dao
interface LocalProfileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: LocalProfileEntity): Long

    @Update
    suspend fun update(profile: LocalProfileEntity)

    @Query("SELECT * FROM local_profiles ORDER BY created_at_epoch_millis ASC")
    suspend fun list(): List<LocalProfileEntity>

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

    @Query("SELECT * FROM accounts WHERE profile_id = :profileId ORDER BY is_default DESC, created_at_epoch_millis ASC")
    suspend fun listByProfile(profileId: Long): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :accountId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, accountId: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE profile_id = :profileId AND is_default = 1 LIMIT 1")
    suspend fun getDefault(profileId: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE profile_id = :profileId")
    suspend fun countByProfile(profileId: Long): Int

    @Delete
    suspend fun delete(account: AccountEntity)
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE profile_id = :profileId AND deleted_at_epoch_millis IS NULL ORDER BY name ASC")
    suspend fun listByProfile(profileId: Long): List<CategoryEntity>

    @Query(
        """
        SELECT * FROM categories
        WHERE profile_id = :profileId
          AND deleted_at_epoch_millis IS NULL
          AND (type = :type OR type = 'both')
        ORDER BY name ASC
        """,
    )
    suspend fun listByType(profileId: Long, type: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :categoryId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, categoryId: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE profile_id = :profileId AND type = :type AND is_protected = 1 LIMIT 1")
    suspend fun getProtectedByType(profileId: Long, type: String): CategoryEntity?
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :transactionId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, transactionId: Long): TransactionEntity?

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

    @Query("DELETE FROM transactions WHERE id = :transactionId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, transactionId: Long)
}

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: TransferEntity): Long

    @Query("SELECT * FROM transfers WHERE id = :transferId AND profile_id = :profileId")
    suspend fun getById(profileId: Long, transferId: Long): TransferEntity?

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

    @Query("DELETE FROM transfers WHERE id = :transferId AND profile_id = :profileId")
    suspend fun deleteById(profileId: Long, transferId: Long)
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

    @Query("SELECT * FROM savings_goals WHERE account_id = :accountId")
    suspend fun listByAccount(accountId: Long): List<SavingsGoalEntity>

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
