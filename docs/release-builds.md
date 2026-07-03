# Release Builds

## Build Variants

The app module currently defines Android build types only. There are no product
flavors.

| Variant | Application ID | Version name | Signing |
| --- | --- | --- | --- |
| `debug` | `dev.horex.moneytracker.debug` | `0.1.0-debug` | Standard Android debug signing |
| `release` | `dev.horex.moneytracker` | `0.1.0` | Local or CI release signing |

Use `debug` for local development and PR validation. Use `release` only for
release preparation.

Release branch policy stays unchanged: normal work goes through `develop`, and
the release branch remains `main`.

## Release Signing

Release signing values are loaded from environment variables first, then from
`money-tracker-signing.properties` in Gradle user home. Keep the properties file
outside the repository so signing secrets cannot be committed.

Supported environment variables:

```powershell
$env:MONEY_TRACKER_RELEASE_STORE_FILE = 'C:\path\to\money-tracker-release.jks'
$env:MONEY_TRACKER_RELEASE_STORE_PASSWORD = '<store password>'
$env:MONEY_TRACKER_RELEASE_KEY_ALIAS = 'money-tracker-release'
$env:MONEY_TRACKER_RELEASE_KEY_PASSWORD = '<key password>'
```

Equivalent local file at `~/.gradle/money-tracker-signing.properties`:

```properties
releaseStoreFile=C:/Users/<user>/.gradle/keystores/money-tracker-release.jks
releaseStorePassword=<store password>
releaseKeyAlias=money-tracker-release
releaseKeyPassword=<key password>
```

`releaseStoreFile` can be absolute or relative to the repository root. Prefer an
absolute path outside the repository, for example under Gradle user home or a
password manager export location. Do not create release keystores or filled
signing properties in the repository tree.

If release signing is missing or the keystore path is invalid, release builds
fail at `:app:validateReleaseSigning` with a clear error. Debug builds and
regular PR validation do not require release secrets.

## Commands

```powershell
.\gradlew.bat assembleDebug
```

Builds the debug APK with the debug application ID suffix.

```powershell
.\gradlew.bat assembleRelease
```

Builds the release APK only when release signing is configured.

## Play Release

Use `docs/play-release-checklist.md` before preparing a Google Play release. It
covers store listing copy, privacy and Data safety declarations, permission
explanations, and release readiness checks.
