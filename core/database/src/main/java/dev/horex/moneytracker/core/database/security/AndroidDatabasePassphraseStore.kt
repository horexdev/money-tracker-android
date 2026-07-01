package dev.horex.moneytracker.core.database.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidDatabasePassphraseStore(
    context: Context,
    private val preferencesName: String = DEFAULT_PREFERENCES_NAME,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val random: SecureRandom = SecureRandom(),
) : DatabasePassphraseStore {
    private val applicationContext = context.applicationContext

    override fun getOrCreatePassphrase(): ByteArray = synchronized(lock) {
        readPassphrase() ?: createPassphrase()
    }

    private fun readPassphrase(): ByteArray? {
        val encryptedPassphrase = preferences.getString(PREF_ENCRYPTED_PASSPHRASE, null)
        val iv = preferences.getString(PREF_IV, null)

        if (encryptedPassphrase == null && iv == null) {
            return null
        }
        check(!encryptedPassphrase.isNullOrBlank() && !iv.isNullOrBlank()) {
            "Encrypted database passphrase is incomplete"
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, decode(iv)),
        )
        return cipher.doFinal(decode(encryptedPassphrase))
    }

    private fun createPassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES)
        random.nextBytes(passphrase)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedPassphrase = cipher.doFinal(passphrase)
        val committed = preferences.edit()
            .putString(PREF_ENCRYPTED_PASSPHRASE, encode(encryptedPassphrase))
            .putString(PREF_IV, encode(cipher.iv))
            .commit()

        check(committed) { "Unable to persist encrypted database passphrase" }
        return passphrase
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
        val existingEntry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        return existingEntry?.secretKey ?: generateSecretKey()
    }

    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private val preferences: SharedPreferences
        get() = applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        const val DEFAULT_PREFERENCES_NAME = "money_tracker_database_passphrase"
        const val DEFAULT_KEY_ALIAS = "money_tracker_database_key"

        internal const val PASSPHRASE_BYTES = 32
        private val lock = Any()
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREF_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
        private const val PREF_IV = "iv"
    }
}
