# Android Validation

## Local Prerequisites

- Android SDK must be available through `ANDROID_HOME` or `ANDROID_SDK_ROOT`.
- Java must be on `PATH`, or `JAVA_HOME` must point to an installed JDK. Android Studio JBR is acceptable locally.
- Run commands from the repository root.

PowerShell setup used by local validation:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

## Required Local Checks

Run these before opening or updating an implementation PR:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat assembleDebug
```

Startup and release-readiness checks:

```powershell
.\gradlew.bat :app:validateStartupPerformanceChecks
.\gradlew.bat :app:validateReleaseBuildChecks
```

For Gradle structure or dependency changes, also run:

```powershell
.\gradlew.bat projects
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
```

## Build Variants And Release Signing

The app module defines `debug` and `release` build variants. Debug builds use
standard Android debug signing and do not require secrets. Release builds require
local or CI signing values and fail fast when signing is not configured.

See `docs/release-builds.md` for the signing properties, environment variables,
and branch policy.

## Startup Performance Checks

The app ships a manual Baseline Profile at
`app/src/main/baseline-prof.txt`. It covers the cold-start path through
`MoneyTrackerApplication`, `MainActivity`, `MoneyTrackerAppContainer`, and the
local profile bootstrap classes. Android Gradle Plugin packages this profile
with app artifacts, and release validation checks that the profile is present
and still names the required startup classes.

Run `.\gradlew.bat :app:validateStartupPerformanceChecks` after changing app
startup, dependency injection, local profile bootstrap, or release build setup.
The task also rejects migration identity markers such as Telegram metadata,
`initData`, `legacy_*`, and source database IDs in the profile.

The profile is intentionally manual for the first offline release. A generated
Macrobenchmark/Baseline Profile module should replace or refresh it once the
release candidate has a stable smoke journey and a dedicated rooted/API 33+
performance device is available.

## Release Checks

Run the startup performance task and `.\gradlew.bat :app:lintVitalRelease`
before preparing a release candidate. CI runs
`.\gradlew.bat :app:validateReleaseBuildChecks`, which verifies release
constants that do not need signing secrets. `lintVitalRelease`, `assembleRelease`,
and `bundleRelease` require the release-signing environment because the release
variant depends on `:app:validateReleaseSigning`.

## CI Checks

GitHub Actions runs the same required Gradle checks for pull requests and pushes to `develop`:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebugAndroidTest
./gradlew assembleDebug
./gradlew :app:validateStartupPerformanceChecks
./gradlew :app:validateReleaseBuildChecks
```

`assembleDebugAndroidTest` compiles instrumentation, Room, and Compose test setup without requiring a connected device. CI installs the Android 36 platform and build tools before running validation.

## Expected Warnings

The current bootstrap stack may print Gradle deprecation warnings and a non-blocking strip warning for `libandroidx.graphics.path.so`. These warnings do not fail validation. Any failed Gradle task, lint error, dependency resolution error, or APK assembly failure blocks review.
