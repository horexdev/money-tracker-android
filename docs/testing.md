# Testing Infrastructure

## Shared Module

`:core:testing` is the shared test-only module. Production modules must not depend on it through `implementation`.

Use it from tests:

```kotlin
dependencies {
    testImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:testing"))
}
```

The module exports:

- `MainDispatcherRule` for coroutine tests that need a controlled `Dispatchers.Main`.
- `MoneyTrackerTestFixtures` for local-only account/category/money fixtures.
- `RoomTestDatabaseConfig` plus `androidx.room:room-testing` for future Room migration and in-memory database tests.
- AndroidX Test, Compose UI test, Espresso, and JUnit artifacts for instrumentation and Compose tests.

## Commands

Run the full local test gate before publishing implementation changes:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat assembleDebug
```

`assembleDebugAndroidTest` compiles instrumentation, Room, and Compose tests without requiring a connected emulator or device. Device execution can be added later with `connectedDebugAndroidTest` when test devices are part of the workflow.
