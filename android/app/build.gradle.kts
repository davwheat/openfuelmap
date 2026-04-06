plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)

    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)

    alias(libs.plugins.stability.analyzer)
    alias(libs.plugins.secrets)
}

val appCompileSdk: Int by rootProject.extra
val appBuildNumber: Int by rootProject.extra
val appVersionName: String by rootProject.extra

android {
    namespace = "dev.davwheat.openfuelmap"
    compileSdk = appCompileSdk

    defaultConfig {
        applicationId = "dev.davwheat.openfuelmap"
        minSdk = 26
        targetSdk = 36
        versionCode = appBuildNumber
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

secrets { propertiesFileName = "local.properties" }

dependencies {
    implementation(libs.androidx.annotation.experimental)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.kotlinx.serialization.core)

    // Hilt
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.dagger.hilt.testing)
    kspAndroidTest(libs.dagger.hilt.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Nav3
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.timber)

    // Feature modules
    implementation(project(":app:api"))
    implementation(project(":data"))
    implementation(project(":forecourts:api"))
    implementation(project(":forecourts:data"))
    implementation(project(":map:api"))
    implementation(project(":map:impl"))
    implementation(project(":list:api"))
    implementation(project(":list:impl"))
    implementation(project(":settings:api"))
    implementation(project(":settings:impl"))
    implementation(project(":stats:api"))
    implementation(project(":stats:data"))
    implementation(project(":stats:impl"))
}
