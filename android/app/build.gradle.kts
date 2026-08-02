/**
 * Open Fuel Map
 * Copyright (C) 2026  David Wheatley
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)

    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)

    alias(libs.plugins.stability.analyzer)
    alias(libs.plugins.ossLicenses)
    alias(libs.plugins.androidx.baselineprofile)
}

val appCompileSdk = rootProject.extra["appCompileSdk"] as Int
val appMinSdk = rootProject.extra["appMinSdk"] as Int
val appBuildNumber = rootProject.extra["appBuildNumber"] as Int
val appVersionName = rootProject.extra["appVersionName"] as String

android {
    namespace = "dev.davwheat.openfuelmap"
    compileSdk = appCompileSdk

    defaultConfig {
        applicationId = "dev.davwheat.openfuelmap"
        minSdk = appMinSdk
        targetSdk = 37
        versionCode = appBuildNumber
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val localPropertiesFile = project.rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                val properties = Properties()
                properties.load(localPropertiesFile.inputStream())

                properties.getProperty("signing.storeFilePath")?.let { storeFile = file(it) }
                storePassword = properties.getProperty("signing.storePassword")
                keyAlias = properties.getProperty("signing.keyAlias")
                keyPassword = properties.getProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // The baseline profile plugin copies this build type into `benchmarkRelease` and
            // `nonMinifiedRelease`, thus the signing config must resolve on a machine without
            // the upload keystore too. Those two builds never ship, so the debug key is enough.
            signingConfig =
                signingConfigs.getByName(
                    if (signingConfigs.getByName("release").storeFile != null) "release"
                    else "debug"
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

baselineProfile {
    // Both renderer flavors run the same UI journey, thus one merged profile in `src/main` is
    // enough and keeps a single file under review.
    mergeIntoMain = true

    // A device generates the profile, not the build. Keeping this off means a normal release
    // build never waits for a connected device.
    automaticGenerationDuringBuild = false
}

androidComponents {
    onVariants(selector().all()) { variant ->
        // The two renderer bundles go into one Play release, thus each needs its own version
        // code. Vulkan keeps the base code, thus Play prefers it where the device supports
        // Vulkan. OpenGL serves the devices that the Vulkan bundle's `uses-feature` hides it
        // from.
        val isOpengl = variant.productFlavors.any { (_, flavor) -> flavor == "opengl" }
        val versionCode = if (isOpengl) appBuildNumber - 1 else appBuildNumber

        variant.outputs.forEach { output -> output.versionCode.set(versionCode) }
    }
}

dependencies {
    implementation(libs.androidx.annotation.experimental)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    // Installs the baseline profile on devices that Play does not serve a cloud profile to.
    implementation(libs.androidx.profileinstaller)
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
    implementation(project(":common:nav"))
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

    baselineProfile(project(":baselineprofile"))
}
