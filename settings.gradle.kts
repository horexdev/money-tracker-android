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
include(":core:balance")
include(":core:categories")
include(":core:common")
include(":core:currency")
include(":core:database")
include(":core:designsystem")
include(":core:money")
include(":core:navigation")
include(":core:notifications")
include(":core:preferences")
include(":core:stats")
include(":core:testing")
include(":core:transactions")
include(":core:transfers")
include(":feature:accounts")
include(":feature:addtransaction")
include(":feature:categories")
include(":feature:home")
include(":feature:history")
include(":feature:stats")
