import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val generatedVersionCode = rootProject.extra["generatedVersionCode"] as Int
val generatedVersionName = rootProject.extra["generatedVersionName"] as String

val releaseSigning = mapOf(
    "storeFile" to providers.gradleProperty("releaseStoreFile").orElse(providers.environmentVariable("ANDROID_KEYSTORE_PATH")).orNull,
    "storePassword" to providers.gradleProperty("releaseStorePassword").orElse(providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")).orNull,
    "keyAlias" to providers.gradleProperty("releaseKeyAlias").orElse(providers.environmentVariable("ANDROID_KEY_ALIAS")).orNull,
    "keyPassword" to providers.gradleProperty("releaseKeyPassword").orElse(providers.environmentVariable("ANDROID_KEY_PASSWORD")).orNull,
)
val hasReleaseSigning = releaseSigning.values.any { !it.isNullOrBlank() }
if (hasReleaseSigning) {
    require(releaseSigning.values.all { !it.isNullOrBlank() }) {
        "Release signing requires all four values: releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword (or their ANDROID_* environment variables)."
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "info.anodsplace.evtimer"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "info.anodsplace.evtimer"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = generatedVersionCode
        versionName = generatedVersionName
    }
    packaging {
        resources {
            excludes.add("/META-INF/AL2.0")
            excludes.add("/META-INF/LGPL2.1")
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigning["storeFile"]))
                storePassword = releaseSigning["storePassword"]
                keyAlias = releaseSigning["keyAlias"]
                keyPassword = releaseSigning["keyPassword"]
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":lib:permissions"))
    implementation(libs.androidx.activity)
    // Splash screen API
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}
