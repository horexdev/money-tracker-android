package dev.horex.moneytracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.horex.moneytracker.core.database.dao.AccountDao
import dev.horex.moneytracker.core.database.dao.BudgetDao
import dev.horex.moneytracker.core.database.dao.CategoryDao
import dev.horex.moneytracker.core.database.dao.ExchangeRateOverrideDao
import dev.horex.moneytracker.core.database.dao.ExchangeRateSnapshotDao
import dev.horex.moneytracker.core.database.dao.GoalTransactionDao
import dev.horex.moneytracker.core.database.dao.LocalProfileDao
import dev.horex.moneytracker.core.database.dao.RecurringTransactionRunDao
import dev.horex.moneytracker.core.database.dao.RecurringTransactionDao
import dev.horex.moneytracker.core.database.dao.SavingsGoalDao
import dev.horex.moneytracker.core.database.dao.TransactionDao
import dev.horex.moneytracker.core.database.dao.TransactionTemplateDao
import dev.horex.moneytracker.core.database.dao.TransferDao
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.BudgetEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateOverrideEntity
import dev.horex.moneytracker.core.database.model.ExchangeRateSnapshotEntity
import dev.horex.moneytracker.core.database.model.GoalTransactionEntity
import dev.horex.moneytracker.core.database.model.LocalProfileEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionRunEntity
import dev.horex.moneytracker.core.database.model.RecurringTransactionEntity
import dev.horex.moneytracker.core.database.model.SavingsGoalEntity
import dev.horex.moneytracker.core.database.model.TransactionEntity
import dev.horex.moneytracker.core.database.model.TransactionTemplateEntity
import dev.horex.moneytracker.core.database.model.TransferEntity

@Database(
    entities = [
        LocalProfileEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransferEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        RecurringTransactionRunEntity::class,
        SavingsGoalEntity::class,
        GoalTransactionEntity::class,
        ExchangeRateSnapshotEntity::class,
        ExchangeRateOverrideEntity::class,
        TransactionTemplateEntity::class,
    ],
    version = MoneyTrackerDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class MoneyTrackerDatabase : RoomDatabase() {
    abstract fun localProfileDao(): LocalProfileDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun recurringTransactionRunDao(): RecurringTransactionRunDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun goalTransactionDao(): GoalTransactionDao
    abstract fun exchangeRateSnapshotDao(): ExchangeRateSnapshotDao
    abstract fun exchangeRateOverrideDao(): ExchangeRateOverrideDao
    abstract fun transactionTemplateDao(): TransactionTemplateDao

    companion object {
        const val DATABASE_NAME = "money_tracker.db"
        const val SCHEMA_VERSION = 3
    }
}
