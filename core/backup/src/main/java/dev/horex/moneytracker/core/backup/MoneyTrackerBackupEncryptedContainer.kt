package dev.horex.moneytracker.core.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val MONEY_TRACKER_BACKUP_ENCRYPTED_CONTAINER_FORMAT = "MoneyTrackerBackupEncryptedContainer"
const val MONEY_TRACKER_BACKUP_ENCRYPTED_CONTAINER_VERSION = 1
const val MONEY_TRACKER_BACKUP_PAYLOAD_ENCODING = "utf-8-json"
const val MONEY_TRACKER_BACKUP_ENCRYPTION_ALGORITHM = "AES-256-GCM"
const val MONEY_TRACKER_BACKUP_KDF_ALGORITHM = "PBKDF2-HMAC-SHA256"
const val MONEY_TRACKER_BACKUP_DEFAULT_KDF_ITERATIONS = 210_000
const val MONEY_TRACKER_BACKUP_DEFAULT_SALT_BYTES = 16
const val MONEY_TRACKER_BACKUP_DEFAULT_NONCE_BYTES = 12
const val MONEY_TRACKER_BACKUP_AES_KEY_LENGTH_BITS = 256

private const val JCA_CIPHER_ALGORITHM = "AES/GCM/NoPadding"
private const val JCA_KEY_ALGORITHM = "AES"
private const val JCA_KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
private const val GCM_TAG_LENGTH_BITS = 128

@Serializable
data class MoneyTrackerBackupEncryptedContainer(
    @SerialName("format")
    val format: String = MONEY_TRACKER_BACKUP_ENCRYPTED_CONTAINER_FORMAT,
    @SerialName("version")
    val version: Int = MONEY_TRACKER_BACKUP_ENCRYPTED_CONTAINER_VERSION,
    @SerialName("payload_format")
    val payloadFormat: String = MONEY_TRACKER_BACKUP_FORMAT,
    @SerialName("payload_version")
    val payloadVersion: Int = MONEY_TRACKER_BACKUP_VERSION,
    @SerialName("payload_encoding")
    val payloadEncoding: String = MONEY_TRACKER_BACKUP_PAYLOAD_ENCODING,
    @SerialName("encryption")
    val encryption: MoneyTrackerBackupEncryptionPayload,
)

@Serializable
data class MoneyTrackerBackupEncryptionPayload(
    @SerialName("algorithm")
    val algorithm: String = MONEY_TRACKER_BACKUP_ENCRYPTION_ALGORITHM,
    @SerialName("kdf")
    val kdf: MoneyTrackerBackupKdfParameters,
    @SerialName("nonce")
    val nonceBase64: String,
    @SerialName("ciphertext")
    val ciphertextBase64: String,
)

@Serializable
data class MoneyTrackerBackupKdfParameters(
    @SerialName("algorithm")
    val algorithm: String = MONEY_TRACKER_BACKUP_KDF_ALGORITHM,
    @SerialName("iterations")
    val iterations: Int = MONEY_TRACKER_BACKUP_DEFAULT_KDF_ITERATIONS,
    @SerialName("salt")
    val saltBase64: String,
    @SerialName("key_length_bits")
    val keyLengthBits: Int = MONEY_TRACKER_BACKUP_AES_KEY_LENGTH_BITS,
)

data class MoneyTrackerBackupEncryptionOptions(
    val kdfIterations: Int = MONEY_TRACKER_BACKUP_DEFAULT_KDF_ITERATIONS,
    val saltSizeBytes: Int = MONEY_TRACKER_BACKUP_DEFAULT_SALT_BYTES,
    val nonceSizeBytes: Int = MONEY_TRACKER_BACKUP_DEFAULT_NONCE_BYTES,
)

open class MoneyTrackerBackupEncryptionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class MoneyTrackerBackupInvalidContainerException(
    message: String,
    cause: Throwable? = null,
) : MoneyTrackerBackupEncryptionException(message, cause)

class MoneyTrackerBackupPasswordRequiredException :
    MoneyTrackerBackupEncryptionException("Encrypted backup requires a password.")

class MoneyTrackerBackupWrongPasswordException(
    cause: Throwable? = null,
) : MoneyTrackerBackupEncryptionException(
    message = "Backup password is incorrect or encrypted backup is corrupted.",
    cause = cause,
)

class MoneyTrackerBackupEncryptedPayloadValidationException(
    val validationResult: MoneyTrackerBackupValidationResult,
) : MoneyTrackerBackupEncryptionException("Decrypted backup payload failed validation.")

object MoneyTrackerBackupEncryptedContainerJson {
    private val secureRandom = SecureRandom()

    fun isEncryptedContainer(value: String): Boolean {
        val root = runCatching {
            MoneyTrackerBackupV1Json.json.parseToJsonElement(value).jsonObject
        }.getOrNull() ?: return false
        return root["format"]?.jsonPrimitive?.contentOrNull == MONEY_TRACKER_BACKUP_ENCRYPTED_CONTAINER_FORMAT
    }

    fun encodeEncryptedBackup(
        backup: MoneyTrackerBackup,
        password: CharArray,
        options: MoneyTrackerBackupEncryptionOptions = MoneyTrackerBackupEncryptionOptions(),
    ): String {
        val backupJson = MoneyTrackerBackupV1Json.encodeToString(backup)
        return encodeEncryptedBackupJson(backupJson, password, options)
    }

    fun encodeEncryptedBackupJson(
        backupJson: String,
        password: CharArray,
        options: MoneyTrackerBackupEncryptionOptions = MoneyTrackerBackupEncryptionOptions(),
    ): String {
        requireUsablePassword(password)
        requireSupportedOptions(options)

        val salt = randomBytes(options.saltSizeBytes)
        val nonce = randomBytes(options.nonceSizeBytes)
        val kdf = MoneyTrackerBackupKdfParameters(
            iterations = options.kdfIterations,
            saltBase64 = salt.encodeBase64(),
        )
        val metadata = MoneyTrackerBackupEncryptedContainer(
            encryption = MoneyTrackerBackupEncryptionPayload(
                kdf = kdf,
                nonceBase64 = nonce.encodeBase64(),
                ciphertextBase64 = "",
            ),
        )
        val ciphertext = crypt(
            mode = Cipher.ENCRYPT_MODE,
            password = password,
            kdf = kdf,
            nonce = nonce,
            input = backupJson.toByteArray(Charsets.UTF_8),
            authenticatedData = metadata.authenticatedData(),
        )
        val container = metadata.copy(
            encryption = metadata.encryption.copy(
                ciphertextBase64 = ciphertext.encodeBase64(),
            ),
        )
        return MoneyTrackerBackupV1Json.json.encodeToString(container)
    }

    fun decodeContainer(value: String): MoneyTrackerBackupEncryptedContainer {
        val container = try {
            MoneyTrackerBackupV1Json.json.decodeFromString(
                MoneyTrackerBackupEncryptedContainer.serializer(),
                value,
            )
        } catch (error: SerializationException) {
            throw MoneyTrackerBackupInvalidContainerException(
                "Encrypted backup container cannot be parsed.",
                error,
            )
        } catch (error: IllegalArgumentException) {
            throw MoneyTrackerBackupInvalidContainerException(
                "Encrypted backup container cannot be parsed.",
                error,
            )
        }
        requireSupportedContainer(container)
        return container
    }

    fun decryptToBackupJson(
        value: String,
        password: CharArray,
    ): String {
        requireUsablePassword(password)
        val container = decodeContainer(value)
        val salt = container.encryption.kdf.saltBase64.decodeBase64("KDF salt")
        val nonce = container.encryption.nonceBase64.decodeBase64("AES-GCM nonce")
        val ciphertext = container.encryption.ciphertextBase64.decodeBase64("AES-GCM ciphertext")
        requireDecodedCryptoInputs(salt, nonce, ciphertext)
        val plaintext = crypt(
            mode = Cipher.DECRYPT_MODE,
            password = password,
            kdf = container.encryption.kdf,
            nonce = nonce,
            input = ciphertext,
            authenticatedData = container.authenticatedData(),
            saltOverride = salt,
        )
        return plaintext.toString(Charsets.UTF_8)
    }

    fun decodeEncryptedBackup(
        value: String,
        password: CharArray,
    ): MoneyTrackerBackup {
        val backupJson = decryptToBackupJson(value, password)
        val validation = MoneyTrackerBackupV1Validator.validateJson(backupJson)
        if (!validation.canImport) {
            throw MoneyTrackerBackupEncryptedPayloadValidationException(validation)
        }
        return MoneyTrackerBackupV1Json.decodeFromString(backupJson)
    }

    private fun crypt(
        mode: Int,
        password: CharArray,
        kdf: MoneyTrackerBackupKdfParameters,
        nonce: ByteArray,
        input: ByteArray,
        authenticatedData: ByteArray,
        saltOverride: ByteArray? = null,
    ): ByteArray {
        val salt = saltOverride ?: kdf.saltBase64.decodeBase64("KDF salt")
        val keyBytes = deriveKey(password, salt, kdf.iterations, kdf.keyLengthBits)
        return try {
            val cipher = Cipher.getInstance(JCA_CIPHER_ALGORITHM)
            val key = SecretKeySpec(keyBytes, JCA_KEY_ALGORITHM)
            cipher.init(mode, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
            cipher.updateAAD(authenticatedData)
            cipher.doFinal(input)
        } catch (error: AEADBadTagException) {
            throw MoneyTrackerBackupWrongPasswordException(error)
        } catch (error: BadPaddingException) {
            throw MoneyTrackerBackupWrongPasswordException(error)
        } catch (error: IllegalBlockSizeException) {
            throw MoneyTrackerBackupWrongPasswordException(error)
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        return try {
            SecretKeyFactory.getInstance(JCA_KDF_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun requireSupportedContainer(container: MoneyTrackerBackupEncryptedContainer) {
        if (container.format != MONEY_TRACKER_BACKUP_ENCRYPTED_CONTAINER_FORMAT) {
            throw MoneyTrackerBackupInvalidContainerException(
                "Unsupported encrypted backup container format: ${container.format}",
            )
        }
        if (container.version != MONEY_TRACKER_BACKUP_ENCRYPTED_CONTAINER_VERSION) {
            throw MoneyTrackerBackupInvalidContainerException(
                "Unsupported encrypted backup container version: ${container.version}",
            )
        }
        if (container.payloadFormat != MONEY_TRACKER_BACKUP_FORMAT ||
            container.payloadVersion != MONEY_TRACKER_BACKUP_VERSION ||
            container.payloadEncoding != MONEY_TRACKER_BACKUP_PAYLOAD_ENCODING
        ) {
            throw MoneyTrackerBackupInvalidContainerException(
                "Unsupported encrypted backup payload contract.",
            )
        }
        if (container.encryption.algorithm != MONEY_TRACKER_BACKUP_ENCRYPTION_ALGORITHM) {
            throw MoneyTrackerBackupInvalidContainerException(
                "Unsupported encrypted backup algorithm: ${container.encryption.algorithm}",
            )
        }
        if (container.encryption.kdf.algorithm != MONEY_TRACKER_BACKUP_KDF_ALGORITHM) {
            throw MoneyTrackerBackupInvalidContainerException(
                "Unsupported backup KDF algorithm: ${container.encryption.kdf.algorithm}",
            )
        }
        if (container.encryption.kdf.iterations <= 0) {
            throw MoneyTrackerBackupInvalidContainerException("Backup KDF iterations must be positive.")
        }
        if (container.encryption.kdf.keyLengthBits != MONEY_TRACKER_BACKUP_AES_KEY_LENGTH_BITS) {
            throw MoneyTrackerBackupInvalidContainerException("Unsupported backup encryption key length.")
        }
    }

    private fun requireDecodedCryptoInputs(
        salt: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
    ) {
        if (salt.isEmpty()) {
            throw MoneyTrackerBackupInvalidContainerException("Backup KDF salt must not be empty.")
        }
        if (nonce.size != MONEY_TRACKER_BACKUP_DEFAULT_NONCE_BYTES) {
            throw MoneyTrackerBackupInvalidContainerException(
                "AES-GCM backup nonce must be $MONEY_TRACKER_BACKUP_DEFAULT_NONCE_BYTES bytes.",
            )
        }
        if (ciphertext.isEmpty()) {
            throw MoneyTrackerBackupInvalidContainerException("Backup ciphertext must not be empty.")
        }
    }

    private fun requireSupportedOptions(options: MoneyTrackerBackupEncryptionOptions) {
        if (options.kdfIterations <= 0) {
            throw MoneyTrackerBackupInvalidContainerException("Backup KDF iterations must be positive.")
        }
        if (options.saltSizeBytes <= 0) {
            throw MoneyTrackerBackupInvalidContainerException("Backup KDF salt size must be positive.")
        }
        if (options.nonceSizeBytes != MONEY_TRACKER_BACKUP_DEFAULT_NONCE_BYTES) {
            throw MoneyTrackerBackupInvalidContainerException(
                "AES-GCM backup nonce size must be $MONEY_TRACKER_BACKUP_DEFAULT_NONCE_BYTES bytes.",
            )
        }
    }

    private fun requireUsablePassword(password: CharArray) {
        if (password.isEmpty() || password.all(Char::isWhitespace)) {
            throw MoneyTrackerBackupInvalidContainerException("Backup password must not be blank.")
        }
    }

    private fun randomBytes(size: Int): ByteArray {
        return ByteArray(size).also(secureRandom::nextBytes)
    }

    private fun MoneyTrackerBackupEncryptedContainer.authenticatedData(): ByteArray {
        return listOf(
            format,
            version.toString(),
            payloadFormat,
            payloadVersion.toString(),
            payloadEncoding,
            encryption.algorithm,
            encryption.kdf.algorithm,
            encryption.kdf.iterations.toString(),
            encryption.kdf.saltBase64,
            encryption.kdf.keyLengthBits.toString(),
            encryption.nonceBase64,
        ).joinToString(separator = "|").toByteArray(Charsets.UTF_8)
    }

    private fun ByteArray.encodeBase64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

    private fun String.decodeBase64(label: String): ByteArray {
        return try {
            Base64.getDecoder().decode(this)
        } catch (error: IllegalArgumentException) {
            throw MoneyTrackerBackupInvalidContainerException(
                "Encrypted backup container has invalid $label.",
                error,
            )
        }
    }
}
