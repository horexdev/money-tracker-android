package dev.horex.moneytracker.core.database.security

interface DatabasePassphraseStore {
    fun getOrCreatePassphrase(): ByteArray
}
