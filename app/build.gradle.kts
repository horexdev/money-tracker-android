import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class ValidateReleaseSigningTask : DefaultTask() {
    @get:Input
    abstract val missingFields: ListProperty<String>

    @get:Optional
    @get:Input
    abstract val storeFilePath: Property<String>

    @TaskAction
    fun validate() {
        val missing = missingFields.get()
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured. Missing fields: " +
                    missing.joinToString() +
                    ". Copy signing.properties.example to GRADLE_USER_HOME/" +
                    "money-tracker-signing.properties or set MONEY_TRACKER_RELEASE_* " +
                    "environment variables.",
            )
        }

        val releaseStoreFile = File(storeFilePath.get())
        if (!releaseStoreFile.isFile) {
            throw GradleException(
                "Release keystore file does not exist: ${releaseStoreFile.absolutePath}",
            )
        }
    }
}

abstract class ValidateStartupPerformanceChecksTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineProfileFile: Property<File>

    @get:Input
    abstract val requiredStartupClasses: ListProperty<String>

    @TaskAction
    fun validate() {
        val profileFile = baselineProfileFile.get()
        if (!profileFile.isFile) {
            throw GradleException("Missing startup baseline profile: ${profileFile.path}")
        }

        val profileText = profileFile.readText()
        if (profileText.isBlank()) {
            throw GradleException("Startup baseline profile is empty: ${profileFile.path}")
        }

        val missingStartupClasses = requiredStartupClasses.get()
            .filterNot { classDescriptor -> profileText.contains(classDescriptor) }
        if (missingStartupClasses.isNotEmpty()) {
            throw GradleException(
                "Startup baseline profile is missing required classes: " +
                    missingStartupClasses.joinToString(),
            )
        }

        val prohibitedIdentityMarkers = listOf(
            "telegram",
            "username",
            "initdata",
            "bot",
            "chat",
            "legacy_",
            "legacy-",
            "source_db",
            "source-db",
            "sourceid",
            "source_id",
        )
        val normalizedProfile = profileText.lowercase()
        val presentMarkers = prohibitedIdentityMarkers.filter(normalizedProfile::contains)
        if (presentMarkers.isNotEmpty()) {
            throw GradleException(
                "Startup baseline profile contains prohibited migration identity markers: " +
                    presentMarkers.joinToString(),
            )
        }
    }
}

abstract class ValidateReleaseBuildChecksTask : DefaultTask() {
    @get:Input
    abstract val releaseApplicationId: Property<String>

    @get:Input
    abstract val releaseVersionCode: Property<Int>

    @get:Input
    abstract val releaseVersionName: Property<String>

    @get:Input
    abstract val releaseDebuggable: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val proguardRulesFile: Property<File>

    @TaskAction
    fun validate() {
        val applicationId = releaseApplicationId.get()
        if (applicationId.endsWith(".debug")) {
            throw GradleException("Release applicationId must not use the debug suffix: $applicationId")
        }

        if (releaseVersionCode.get() < 1) {
            throw GradleException("Release versionCode must be positive.")
        }

        if (releaseVersionName.get().isBlank()) {
            throw GradleException("Release versionName must not be blank.")
        }

        if (releaseDebuggable.get()) {
            throw GradleException("Release build must not be debuggable.")
        }

        val proguardFile = proguardRulesFile.get()
        if (!proguardFile.isFile) {
            throw GradleException("Release ProGuard rules file is missing: ${proguardFile.path}")
        }
    }
}

val releaseSigningPropertiesFile = gradle.gradleUserHomeDir.resolve("money-tracker-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningValue(propertyName: String, environmentVariableName: String): String? =
    providers.environmentVariable(environmentVariableName).orNull
        ?.takeIf(String::isNotBlank)
        ?: releaseSigningProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)

val releaseStoreFilePath = releaseSigningValue(
    propertyName = "releaseStoreFile",
    environmentVariableName = "MONEY_TRACKER_RELEASE_STORE_FILE",
)
val releaseStorePassword = releaseSigningValue(
    propertyName = "releaseStorePassword",
    environmentVariableName = "MONEY_TRACKER_RELEASE_STORE_PASSWORD",
)
val releaseKeyAlias = releaseSigningValue(
    propertyName = "releaseKeyAlias",
    environmentVariableName = "MONEY_TRACKER_RELEASE_KEY_ALIAS",
)
val releaseKeyPassword = releaseSigningValue(
    propertyName = "releaseKeyPassword",
    environmentVariableName = "MONEY_TRACKER_RELEASE_KEY_PASSWORD",
)
val releaseSigningFields = mapOf(
    "releaseStoreFile" to releaseStoreFilePath,
    "releaseStorePassword" to releaseStorePassword,
    "releaseKeyAlias" to releaseKeyAlias,
    "releaseKeyPassword" to releaseKeyPassword,
)
val missingReleaseSigningFields = releaseSigningFields
    .filterValues { value -> value.isNullOrBlank() }
    .keys
    .sorted()
val isReleaseSigningConfigured = missingReleaseSigningFields.isEmpty()
val moneyTrackerApplicationId = "dev.horex.moneytracker"
val moneyTrackerVersionCode = 1
val moneyTrackerVersionName = "0.1.0"
val isReleaseDebuggable = false

android {
    namespace = "dev.horex.moneytracker"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = moneyTrackerApplicationId
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = moneyTrackerVersionCode
        versionName = moneyTrackerVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (isReleaseSigningConfigured) {
                storeFile = rootProject.file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-debug"
        }

        release {
            if (isReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isDebuggable = isReleaseDebuggable
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
}

val validateReleaseSigning = tasks.register<ValidateReleaseSigningTask>("validateReleaseSigning") {
    group = "verification"
    description = "Verifies that release signing credentials are configured before release builds."

    missingFields.set(missingReleaseSigningFields)
    releaseStoreFilePath?.let { path ->
        storeFilePath.set(rootProject.file(path).absolutePath)
    }
}

val validateStartupPerformanceChecks = tasks.register<ValidateStartupPerformanceChecksTask>(
    "validateStartupPerformanceChecks",
) {
    group = "verification"
    description = "Verifies startup baseline profile coverage and migration identity safety."

    baselineProfileFile.set(layout.projectDirectory.file("src/main/baseline-prof.txt").asFile)
    requiredStartupClasses.set(
        listOf(
            "Ldev/horex/moneytracker/MoneyTrackerApplication;",
            "Ldev/horex/moneytracker/MainActivity;",
            "Ldev/horex/moneytracker/MoneyTrackerAppContainer;",
            "Ldev/horex/moneytracker/core/database/profile/LocalProfileRepository;",
            "Ldev/horex/moneytracker/core/database/seed/DefaultProfileSeedRepository;",
        ),
    )
}

tasks.named("check") {
    dependsOn(validateStartupPerformanceChecks)
}

val validateReleaseBuildChecks = tasks.register<ValidateReleaseBuildChecksTask>(
    "validateReleaseBuildChecks",
) {
    group = "verification"
    description = "Verifies release build constants that do not require signing secrets."

    releaseApplicationId.set(moneyTrackerApplicationId)
    releaseVersionCode.set(moneyTrackerVersionCode)
    releaseVersionName.set(moneyTrackerVersionName)
    releaseDebuggable.set(isReleaseDebuggable)
    proguardRulesFile.set(layout.projectDirectory.file("proguard-rules.pro").asFile)
}

tasks.named("check") {
    dependsOn(validateReleaseBuildChecks)
}

tasks.matching { task -> task.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
}

dependencies {
    implementation(project(":core:accounts"))
    implementation(project(":core:balance"))
    implementation(project(":core:backup"))
    implementation(project(":core:background"))
    implementation(project(":core:budgets"))
    implementation(project(":core:categories"))
    implementation(project(":core:currency"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:notifications"))
    implementation(project(":core:preferences"))
    implementation(project(":core:recurring"))
    implementation(project(":core:savings"))
    implementation(project(":core:stats"))
    implementation(project(":core:templates"))
    implementation(project(":core:transactions"))
    implementation(project(":feature:accounts"))
    implementation(project(":feature:addtransaction"))
    implementation(project(":feature:budgets"))
    implementation(project(":feature:categories"))
    implementation(project(":feature:home"))
    implementation(project(":feature:history"))
    implementation(project(":feature:recurring"))
    implementation(project(":feature:savings"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:templates"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(project(":core:testing"))
}
