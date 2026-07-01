# Database Encryption

## Scope

`core:database` opens the production Room database through SQLCipher.

The device database is protected by a random 256-bit SQLCipher passphrase. The passphrase is encrypted with an AES-GCM key generated in Android Keystore and stored in app-private `SharedPreferences`. The Keystore key is device-bound and non-exportable; portable backup encryption must use a separate backup key flow.

## Runtime Flow

1. `MoneyTrackerDatabaseFactory.createEncrypted()` loads the SQLCipher native library.
2. `AndroidDatabasePassphraseStore` reads or creates the database passphrase.
3. New passphrases are generated with `SecureRandom`.
4. The passphrase is encrypted with `AES/GCM/NoPadding` using a key under alias `money_tracker_database_key` in `AndroidKeyStore`.
5. Room is created with `SupportOpenHelperFactory` from `net.zetetic:sqlcipher-android`.

The plaintext passphrase is only used in memory to create the SQLCipher open helper. It must not be logged, exposed through UI state, exported in backups, or reused for portable backup files.

## Validation

- `MoneyTrackerDatabaseSchemaTest.databaseEncryptionContractUsesKeystoreAndAvoidsLogs` keeps the SQLCipher/Keystore integration visible in JVM checks and guards against database-module logging calls.
- `EncryptedMoneyTrackerDatabaseTest.encryptedDatabaseOpensAndReopens` opens the encrypted database, writes a local profile, closes it, and reopens it with the same Keystore-backed passphrase store.
- `assembleDebugAndroidTest` compiles the encrypted database instrumentation test; running the test still requires an Android device or emulator.
