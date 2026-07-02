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

## CI Checks

GitHub Actions runs the same required Gradle checks for pull requests and pushes to `develop`:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebugAndroidTest
./gradlew assembleDebug
```

`assembleDebugAndroidTest` compiles instrumentation, Room, and Compose test setup without requiring a connected device. CI installs the Android 36 platform and build tools before running validation.

## Expected Warnings

The current bootstrap stack may print Gradle deprecation warnings and a non-blocking strip warning for `libandroidx.graphics.path.so`. These warnings do not fail validation. Any failed Gradle task, lint error, dependency resolution error, or APK assembly failure blocks review.
