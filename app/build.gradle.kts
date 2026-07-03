import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
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

android {
    namespace = "dev.horex.moneytracker"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.horex.moneytracker"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

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
            isDebuggable = false
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
    implementation(project(":core:stats"))
    implementation(project(":core:transactions"))
    implementation(project(":feature:accounts"))
    implementation(project(":feature:addtransaction"))
    implementation(project(":feature:categories"))
    implementation(project(":feature:home"))
    implementation(project(":feature:history"))
    implementation(project(":feature:stats"))

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
