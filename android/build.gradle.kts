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
import com.android.build.api.dsl.CommonExtension
import com.diffplug.spotless.extra.wtp.EclipseWtpFormatterStep
import java.text.SimpleDateFormat
import java.time.Duration

plugins {
    alias(libs.plugins.spotless)

    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false

    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false

    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false

    alias(libs.plugins.stability.analyzer) apply false

    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
}

ext {
    set("appCompileSdk", 37)
    set("appMinSdk", 26)

    val appVersionMajor = 0
    val appVersionMinor = 1
    val appVersionPoint = 1
    set("appVersionMajor", appVersionMajor)
    set("appVersionMinor", appVersionMinor)
    set("appVersionPoint", appVersionPoint)
    set("appVersionName", "$appVersionMajor.$appVersionMinor.$appVersionPoint")
    set(
        "appBuildNumber",
        run {
            val df = SimpleDateFormat("yyyyMMdd")
            val date = java.time.LocalDateTime.now()
            val seconds =
                (Duration.between(date.withSecond(0).withMinute(0).withHour(0), date).seconds /
                    86400) * 99.0
            val twoDigitSuffix = seconds.toInt()

            Integer.parseInt(df.format(java.util.Date()) + String.format("%02d", twoDigitSuffix))
        },
    )
}

subprojects {
    afterEvaluate {
        // Each Android module needs the `mapRenderer` dimension, not only the two that use it.
        // Gradle matches variants along the full dependency chain, thus a module between `:app`
        // and `:common:maps` without the dimension breaks the match.
        //
        // `:common:maps` declares the same dimension itself, because its `vulkanApi` and
        // `openglApi` configurations must exist while its own `dependencies` block runs, which
        // is before this callback. The guards below make the two declarations merge.
        extensions.findByType<CommonExtension>()?.apply {
            if (!flavorDimensions.contains("mapRenderer")) {
                flavorDimensions += "mapRenderer"
            }

            //noinspection WrongGradleMethod
            listOf("vulkan", "opengl").forEach { flavor ->
                if (productFlavors.findByName(flavor) == null) {
                    productFlavors.create(flavor).dimension = "mapRenderer"
                }
            }
        }
    }
}

spotless {
    java {
        target("**/src/**/*.java")
        googleJavaFormat("1.35.0").reflowLongStrings()
        formatAnnotations()

        licenseHeaderFile(rootProject.file("./LICENSE_HEADER.java"))
    }

    kotlin {
        target("**/src/**/*.kt", "**/*.kts")
        ktfmt("0.62").kotlinlangStyle()

        licenseHeaderFile(
            rootProject.file("./LICENSE_HEADER.kt"),
            """(plugins \{|pluginManagement \{|import |package |@file)""",
        )
    }

    format("xml") {
        target(
            fileTree(".") {
                include("**/*.xml")
                exclude("**/build/**")
                exclude(".idea/**")
                exclude("LICENSE_HEADER.xml")
            }
        )

        endWithNewline()
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        eclipseWtp(EclipseWtpFormatterStep.XML).configFile("xml.prefs")

        licenseHeaderFile(rootProject.file("./LICENSE_HEADER.xml"), """(<[a-zA-Z])""")
            .named("GPL3")
            .onlyIfContentMatches("GNU General Public License")
            .skipLinesMatching("""<\?xml|<!DOCTYPE""")
    }
}
