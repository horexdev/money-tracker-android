plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.horex.moneytracker.core.money"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
}

dependencies {
    testImplementation(libs.junit)
}
