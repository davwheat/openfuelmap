plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val appCompileSdk: Int by rootProject.extra
val appMinSdk: Int by rootProject.extra

android {
    namespace = "dev.davwheat.openfuelmap.forecourts.data"
    compileSdk = appCompileSdk

    defaultConfig {
        minSdk = appMinSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.annotation.experimental)

    implementation(project(":forecourts:api"))
    implementation(project(":data"))

    implementation(platform(libs.square.okhttp.bom))
    implementation(libs.square.okhttp)
    implementation(libs.square.okhttp.loggingInterceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
}
