pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MoneyTrackerAndroid"
include(":app")
include(":core:accounts")
include(":core:common")
include(":core:database")
include(":core:designsystem")
include(":core:navigation")
include(":core:preferences")
include(":core:testing")
include(":feature:home")
