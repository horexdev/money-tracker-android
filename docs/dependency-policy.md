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
| Navigation Compose | 2.9.8 | Root `NavHost` and Android system back integration. |
| Compose Material Icons | BOM-managed | Bottom navigation icon set resolved through the Compose BOM. |
| JUnit | 4.13.2 | Unit-test baseline until the dedicated test infrastructure task expands coverage. |

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
.\gradlew.bat assembleDebug
```

General Android validation rules live in `docs/android-validation.md`.
