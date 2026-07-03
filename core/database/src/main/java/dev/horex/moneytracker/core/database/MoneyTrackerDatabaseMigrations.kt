package dev.horex.moneytracker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.horex.moneytracker.core.database.model.SystemCategoryLocalization

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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_transactions_profile_id_is_adjustment_created_at_epoch_millis_id`
                ON `transactions` (`profile_id`, `is_adjustment`, `created_at_epoch_millis`, `id`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_transactions_profile_id_account_id_is_adjustment_created_at_epoch_millis_id`
                ON `transactions` (`profile_id`, `account_id`, `is_adjustment`, `created_at_epoch_millis`, `id`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_transactions_profile_id_category_id_is_adjustment_created_at_epoch_millis_id`
                ON `transactions` (`profile_id`, `category_id`, `is_adjustment`, `created_at_epoch_millis`, `id`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_transactions_profile_id_type_is_adjustment_created_at_epoch_millis_id`
                ON `transactions` (`profile_id`, `type`, `is_adjustment`, `created_at_epoch_millis`, `id`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_transactions_profile_id_currency_code_is_adjustment_created_at_epoch_millis_id`
                ON `transactions` (`profile_id`, `currency_code`, `is_adjustment`, `created_at_epoch_millis`, `id`)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `localization_key` TEXT")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_categories_profile_id_localization_key`
                ON `categories` (`profile_id`, `localization_key`)
                """.trimIndent(),
            )
            SystemCategoryLocalization.definitions.forEach { definition ->
                val distinctNames = definition.names.values.distinct()
                val placeholders = distinctNames.joinToString(separator = ", ") { "?" }
                db.execSQL(
                    """
                    UPDATE `categories`
                    SET `localization_key` = ?
                    WHERE `localization_key` IS NULL
                      AND `type` = ?
                      AND `icon` = ?
                      AND LOWER(`color`) = LOWER(?)
                      AND `is_protected` = ?
                      AND `name` IN ($placeholders)
                    """.trimIndent(),
                    arrayOf<Any>(
                        definition.localizationKey,
                        definition.type,
                        definition.icon,
                        definition.color,
                        if (definition.isProtected) 1 else 0,
                        *distinctNames.toTypedArray(),
                    ),
                )
            }
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
