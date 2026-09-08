plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    signingConfigs {
        create("release") {
            val releaseKeyPath = providers.environmentVariable("ZHANZHUANG_KEYSTORE_PATH")
            storeFile = releaseKeyPath.orNull?.let(::file)
            storePassword = providers.environmentVariable("ZHANZHUANG_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("ZHANZHUANG_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("ZHANZHUANG_KEY_PASSWORD").orNull
        }
    }

    buildTypes.named("release") {
        signingConfig = signingConfigs.getByName("release")
        // Keep the first Play candidate debuggable until a separately verified R8 policy exists.
        isMinifyEnabled = false
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
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.fragment.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    ksp(libs.androidx.room.compiler)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
