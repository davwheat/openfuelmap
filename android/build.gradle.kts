import com.diffplug.spotless.extra.wtp.EclipseWtpFormatterStep
import java.text.SimpleDateFormat
import java.time.Duration

// Top-level build file where you can add configuration options common to all sub-projects/modules.
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
    alias(libs.plugins.secrets) apply false
}

ext {
    set("appCompileSdk", 37)
    set("appMinSdk", 26)

    val appVersionMajor = 0
    val appVersionMinor = 0
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

spotless {
    java {
        target("**/src/**/*.java")
        googleJavaFormat("1.35.0").reflowLongStrings()
        formatAnnotations()
    }

    kotlin {
        target("**/src/**/*.kt", "**/*.kts")
        ktfmt("0.62").kotlinlangStyle()
    }

    format("xml") {
        target(
            fileTree(".") {
                include("**/*.xml")
                exclude("**/build/**")
            }
        )

        endWithNewline()
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        eclipseWtp(EclipseWtpFormatterStep.XML).configFile("xml.prefs")
    }
}
