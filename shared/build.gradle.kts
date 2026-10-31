import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.android.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "info.anodsplace.evtimer.generated.resources"
}

kotlin {
    android {
        namespace = "info.anodsplace.evtimer.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        androidMain { kotlin.srcDir("src/jvmAndroidMain/kotlin") }
        jvmMain { kotlin.srcDir("src/jvmAndroidMain/kotlin") }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
        jvmMain.dependencies { implementation(libs.sqldelight.sqlite.driver) }
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(project(":lib:viewmodel"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.animation)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.navigation3)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.androidx.navigation3.ui)
            // Koin types are part of the public initKoin() API used by the app modules
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.datastore.preferences)

            implementation(libs.androidx.graphics.shapes)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmTest.dependencies {
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}

sqldelight {
    databases {
        create("ChargingDatabase") {
            packageName.set("info.anodsplace.evtimer.db")
            verifyMigrations.set(true)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            dialect(libs.sqldelight.dialect)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
