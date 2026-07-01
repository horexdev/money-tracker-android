package dev.horex.moneytracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import dev.horex.moneytracker.core.database.security.DatabasePassphraseStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object MoneyTrackerDatabaseFactory {
    fun createEncrypted(
        context: Context,
        passphraseStore: DatabasePassphraseStore = AndroidDatabasePassphraseStore(context),
        databaseName: String = MoneyTrackerDatabase.DATABASE_NAME,
    ): MoneyTrackerDatabase {
        SqlCipherLibraryLoader.ensureLoaded()
        return Room.databaseBuilder(
            context.applicationContext,
            MoneyTrackerDatabase::class.java,
            databaseName,
        )
            .openHelperFactory(createSqlCipherFactory(passphraseStore.getOrCreatePassphrase()))
            .addMigrations(*MoneyTrackerDatabaseMigrations.ALL)
            .build()
    }

    internal fun createSqlCipherFactory(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
        require(passphrase.isNotEmpty()) { "SQLCipher passphrase must not be empty" }
        return SupportOpenHelperFactory(passphrase.copyOf())
    }
}

private object SqlCipherLibraryLoader {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) {
            return
        }
        synchronized(this) {
            if (!loaded) {
                System.loadLibrary("sqlcipher")
                loaded = true
            }
        }
    }
}
