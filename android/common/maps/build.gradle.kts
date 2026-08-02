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
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

val appCompileSdk = rootProject.extra["appCompileSdk"] as Int
val appMinSdk = rootProject.extra["appMinSdk"] as Int

android {
    namespace = "dev.davwheat.openfuelmap.common.maps"
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
    buildFeatures { compose = true }

    // Declared here, and not only in the root build file, because the `vulkanApi` and `openglApi`
    // configurations must exist while the `dependencies` block below runs. The root declaration
    // happens in an `afterEvaluate` callback, thus too late for this module.
    flavorDimensions += "mapRenderer"
    productFlavors {
        create("vulkan") { dimension = "mapRenderer" }
        create("opengl") { dimension = "mapRenderer" }
    }
}

dependencies {
    // `api` gives MapLibreMap, Style, and the annotation managers (SymbolManager, CircleManager,
    // and the others) to the modules that use this one. They do not declare MapLibre themselves.
    "vulkanApi"(libs.maplibre.native.vulkan)
    "openglApi"(libs.maplibre.native.opengl)
    // The plugin has an older android-sdk. Remove it, thus only the version above is on the
    // classpath.
    api(libs.maplibre.plugin.annotation) {
        exclude(group = "org.maplibre.gl", module = "android-sdk")
    }

    implementation(project(":common:ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.iconsCore)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
