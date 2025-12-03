import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Generate version based on current timestamp
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

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "EvTimerApp"
            isStatic = true
        }
    }
    
    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity) // fixed alias
            // Splash screen API
            implementation(libs.androidx.core.splashscreen)
            implementation(project(":lib:permissions"))
            // WorkManager removed (using foreground service for progress)
        }
        commonMain.dependencies {
            implementation(project(":lib:viewmodel"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.animation) // Added for AnimatedVisibility and other animation APIs
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.datastore.preferences)

            implementation(libs.androidx.graphics.core)
            implementation(libs.androidx.graphics.path)
            implementation(libs.androidx.graphics.shapes)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

android {
    namespace = "info.anodsplace.evtimer"
    compileSdk = 36

    defaultConfig {
        applicationId = "info.anodsplace.evtimer"
        minSdk = 33
        targetSdk = 36
        versionCode = generatedVersionCode
        versionName = generatedVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "info.anodsplace.evtimer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "info.anodsplace.evtimer"
            packageVersion = generatedDesktopVersion
        }
    }
}

// Task to update iOS version info in Config.xcconfig
tasks.register("updateIosVersion") {
    val configFile = file("${rootProject.projectDir}/iosApp/Configuration/Config.xcconfig")
    
    doLast {
        if (configFile.exists()) {
            var content = configFile.readText()
            // Update CURRENT_PROJECT_VERSION (versionCode equivalent) - anchored to line start
            content = content.replace(
                Regex("(?m)^CURRENT_PROJECT_VERSION=.*"),
                "CURRENT_PROJECT_VERSION=$generatedVersionCode"
            )
            // Update MARKETING_VERSION (versionName equivalent) - anchored to line start
            content = content.replace(
                Regex("(?m)^MARKETING_VERSION=.*"),
                "MARKETING_VERSION=$generatedVersionName"
            )
            configFile.writeText(content)
            println("Updated iOS version: CURRENT_PROJECT_VERSION=$generatedVersionCode, MARKETING_VERSION=$generatedVersionName")
        }
    }
}
