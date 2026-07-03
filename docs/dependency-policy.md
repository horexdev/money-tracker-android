# Dependency Policy

## Source Of Truth

`gradle/libs.versions.toml` is the source of truth for Gradle plugins, Android SDK targets, Java target, runtime libraries, and test libraries.

The Gradle wrapper version is pinned in `gradle/wrapper/gradle-wrapper.properties`; the catalog mirrors that version so dependency reviews can see the whole toolchain in one place.

## Current Targets

| Area | Version | Notes |
| --- | --- | --- |
| Gradle wrapper | 9.6.1 | Pinned with `distributionSha256Sum`. |
| Android Gradle Plugin | 9.2.1 | Uses AGP 9 built-in Kotlin support. |
| Kotlin / Compose compiler plugin | 2.4.0 | Compose compiler plugin matches Kotlin. |
| Java target | 17 | Android Studio JBR 21 can compile target 17 bytecode. |
| compileSdk / targetSdk | 36 | Matches the installed stable Android SDK platform used by bootstrap validation. |
| minSdk | 26 | Conservative baseline for offline-first Android app support. |
| Compose BOM | 2026.06.00 | All Compose runtime artifacts are resolved through the BOM. |
| Activity Compose | 1.13.0 | Minimal Activity integration for the bootstrap shell. |
| AndroidX DataStore Preferences | 1.2.1 | Device-local app preferences and active profile pointer exposed through Flow. |
| Navigation Compose | 2.9.8 | Root `NavHost` and Android system back integration. |
| Compose Material Icons | BOM-managed | Bottom navigation icon set resolved through the Compose BOM. |
| KSP | 2.3.9 | Kotlin symbol processing for Room compiler integration. |
| AndroidX Room Runtime / KTX / Compiler | 2.8.1 | Local Room database contract and DAO implementation generation. |
| AndroidX Room Testing | 2.8.1 | Room migration/in-memory database test support for data-layer tasks. |
| AndroidX SQLite | 2.6.2 | SupportSQLite API required by SQLCipher for Android Room integration. |
| SQLCipher for Android | 4.16.0 | Encrypted Room database open helper for local financial storage. |
| AndroidX Test Core / Runner / Rules | 1.7.0 | Instrumentation and Android framework test baseline. |
| AndroidX Test Ext JUnit | 1.3.0 | AndroidJUnit4 integration for instrumentation tests. |
| AndroidX Espresso | 3.7.0 | Android test assertion baseline used by test artifacts. |
| AndroidX WorkManager | 2.11.2 | Local-only periodic background work foundation with custom worker factory hooks. |
| Kotlinx Coroutines Android | 1.11.0 | Runtime coroutine classes aligned with coroutine test tooling used by connected Compose tests. |
| Kotlinx Coroutines Test | 1.11.0 | Coroutine dispatcher and virtual-time test support. |
| JUnit | 4.13.2 | Unit-test baseline until the dedicated test infrastructure task expands coverage. |

`core:designsystem` owns Material 3 theme configuration, app semantic colors, typography, shapes, spacing, and shared Compose components. Feature modules should depend on that module instead of defining their own app theme or base component styles.

`core:testing` owns shared test fixtures, coroutine test rules, and exported Room/Compose/AndroidX test dependencies. Production code must not depend on it; use it only through `testImplementation` or `androidTestImplementation`.

`core:database` owns the Room schema, entities, DAO contracts, exported schema snapshots, and Room compiler setup. Feature modules should not define database tables directly.

`core:database` also owns encrypted database opening through SQLCipher and Android Keystore. Device-bound SQLCipher passphrases must not be reused for portable backup encryption.

`core:preferences` owns Preferences DataStore setup, app preference mapping, and the DataStore-backed active profile pointer. It must not store financial records or source identity fields.

`core:background` owns WorkManager integration, periodic/test work request creation, custom worker factory wiring, and idempotency hook interfaces. It must stay local-only unless a future task explicitly changes the background contract.

## Rules

- Add every new plugin or dependency through `libs.versions.toml`.
- Do not use dynamic versions, version ranges, snapshots, or changing modules.
- Prefer stable releases. Pre-release artifacts require an issue note with the reason and rollback path.
- Keep runtime dependencies minimal in bootstrap/foundation tasks; add domain, database, network, and test libraries in the task that needs them.
- Use the Compose BOM for Compose artifacts instead of pinning individual Compose module versions.
- Do not define repositories inside subprojects. Repositories stay centralized in `settings.gradle.kts`.
- Dependency reviews must inspect the resolved graph for unexpected version selection. AndroidX and Compose are aligned through their published metadata and the Compose BOM, so do not use global `failOnVersionConflict()`.

## Required Checks

Run these before opening a PR that changes dependency or toolchain versions:

```powershell
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat assembleDebug
```

General Android validation rules live in `docs/android-validation.md`.
