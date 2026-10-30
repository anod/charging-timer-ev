import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlin.multiplatform.android.library) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

// Generate version based on current timestamp, shared by androidApp, desktopApp and iosApp
// versionCode: Minutes since 2024-01-01 00:00:00 UTC (monotonically increasing, fits in Int)
//              Max Int (2,147,483,647) allows ~4083 years from epoch, so overflow won't occur until year 6107
// versionName: YYYY.MMDD.HHmm format (e.g., 2025.1203.0654)
// desktopVersion: MAJOR.MINOR.BUILD format for Windows MSI compatibility
//                 MAJOR = year - 2024 (0-255), MINOR = month (1-12), BUILD = day*1440 + hour*60 + minute (max 46079)
val buildTime: Instant = Instant.now()
val utcTime: LocalDateTime = LocalDateTime.ofInstant(buildTime, ZoneOffset.UTC)
val versionEpoch: LocalDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
val generatedVersionCode: Int = Duration.between(versionEpoch, utcTime).toMinutes().toInt()
val generatedVersionName: String = utcTime.format(DateTimeFormatter.ofPattern("yyyy.MMdd.HHmm"))
val desktopVersionMajor: Int = utcTime.year - 2024
val desktopVersionMinor: Int = utcTime.monthValue
val desktopVersionBuild: Int = utcTime.dayOfMonth * 1440 + utcTime.hour * 60 + utcTime.minute
val generatedDesktopVersion: String = "$desktopVersionMajor.$desktopVersionMinor.$desktopVersionBuild"

extra["generatedVersionCode"] = generatedVersionCode
extra["generatedVersionName"] = generatedVersionName
extra["generatedDesktopVersion"] = generatedDesktopVersion

// Task to update iOS version info in Config.xcconfig
tasks.register("updateIosVersion") {
    val configFile = file("iosApp/Configuration/Config.xcconfig")
    val versionCode = generatedVersionCode
    val versionName = generatedVersionName

    doLast {
        if (configFile.exists()) {
            var content = configFile.readText()
            // Update CURRENT_PROJECT_VERSION (versionCode equivalent) - anchored to line start
            content = content.replace(
                Regex("(?m)^CURRENT_PROJECT_VERSION=.*"),
                "CURRENT_PROJECT_VERSION=$versionCode"
            )
            // Update MARKETING_VERSION (versionName equivalent) - anchored to line start
            content = content.replace(
                Regex("(?m)^MARKETING_VERSION=.*"),
                "MARKETING_VERSION=$versionName"
            )
            configFile.writeText(content)
            println("Updated iOS version: CURRENT_PROJECT_VERSION=$versionCode, MARKETING_VERSION=$versionName")
        }
    }
}
