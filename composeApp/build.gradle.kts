import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    /*
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    */
    
    /*
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "EvTimerApp"
            isStatic = true
        }
    }
    */
    
    jvm()

    sourceSets {
        /*
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity) // fixed alias
            // Splash screen API
            implementation(libs.androidx.core.splashscreen)
            implementation(project(":lib:permissions"))
            // WorkManager removed (using foreground service for progress)
        }
        */
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

/*
android {
    namespace = "info.anodsplace.evtimer"
    compileSdk = 36

    defaultConfig {
        applicationId = "info.anodsplace.evtimer"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
*/

/*
dependencies {
    debugImplementation(compose.uiTooling)
}
*/

compose.desktop {
    application {
        mainClass = "info.anodsplace.evtimer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "info.anodsplace.evtimer"
            packageVersion = "1.0.0"
        }
    }
}
