package dev.horex.moneytracker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MoneyTrackerDatabaseMigrations {
    const val INITIAL_VERSION = 1

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recurring_transaction_runs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `profile_id` INTEGER NOT NULL,
                    `recurring_transaction_id` INTEGER NOT NULL,
                    `scheduled_for_epoch_millis` INTEGER NOT NULL,
                    `transaction_id` INTEGER,
                    `processed_at_epoch_millis` INTEGER NOT NULL,
                    FOREIGN KEY(`profile_id`) REFERENCES `local_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`recurring_transaction_id`) REFERENCES `recurring_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_recurring_transaction_runs_profile_id`
                ON `recurring_transaction_runs` (`profile_id`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_recurring_transaction_runs_recurring_transaction_id`
                ON `recurring_transaction_runs` (`recurring_transaction_id`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_recurring_transaction_runs_transaction_id`
                ON `recurring_transaction_runs` (`transaction_id`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                `index_recurring_transaction_runs_profile_id_recurring_transaction_id_scheduled_for_epoch_millis`
                ON `recurring_transaction_runs` (
                    `profile_id`,
                    `recurring_transaction_id`,
                    `scheduled_for_epoch_millis`
                )
                """.trimIndent(),
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
