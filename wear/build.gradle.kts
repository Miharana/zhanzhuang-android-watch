plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.zhanzhuang.timer.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.zhanzhuang.timer"
        minSdk = 33
        targetSdk = 36
        versionCode = 2_000_001
        versionName = "0.1.0"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.health.services.client)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    ksp(libs.androidx.room.compiler)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
