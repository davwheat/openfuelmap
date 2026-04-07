plugins { alias(libs.plugins.android.library) }

val appCompileSdk: Int by rootProject.extra
val appMinSdk: Int by rootProject.extra

android {
    namespace = "dev.davwheat.openfuelmap.common.nav"
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

dependencies { implementation(libs.androidx.navigation3.runtime) }
