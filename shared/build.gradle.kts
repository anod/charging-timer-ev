import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.android.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val generatedLicenseResources = layout.buildDirectory.dir("generated/licenseResources")
// Prefer packaged apps' resolved versions over less-constrained shared configurations.
val licenseReports = listOf(project(":androidApp"), project(":desktopApp"), project).map {
    it.layout.buildDirectory.file("generated/aboutLibraries/aboutlibraries.json")
}
val mergeLibraryDefinitions = tasks.register("mergeLibraryDefinitions") {
    val inputFiles = licenseReports.map { it.get().asFile }
    val output = generatedLicenseResources.get().file("files/aboutlibraries.json").asFile
    dependsOn(":shared:exportLibraryDefinitions", ":androidApp:exportLibraryDefinitions", ":desktopApp:exportLibraryDefinitions")
    inputs.files(inputFiles)
    outputs.dir(generatedLicenseResources)
    doLast {
        val reports = inputFiles.map { requireNotNull(JsonSlurper().parse(it) as? Map<*, *>) }
        val libraries = reports.flatMap { requireNotNull(it["libraries"] as? List<*>) }
            .distinctBy { requireNotNull(it as? Map<*, *>)["uniqueId"] }
        val licenses = reports.flatMap { requireNotNull(it["licenses"] as? Map<*, *>).entries }
            .distinctBy { it.key }
            .associate { it.key to it.value }
        check(libraries.isNotEmpty() && licenses.isNotEmpty()) { "Generated OSS license metadata is empty" }
        output.parentFile.mkdirs()
        output.writeText(JsonOutput.toJson(mapOf("libraries" to libraries, "licenses" to licenses)))
    }
}

val appResources = layout.buildDirectory.dir("generated/appResources")
val prepareAppResources = tasks.register<Sync>("prepareAppResources") {
    from(layout.projectDirectory.dir("src/commonMain/composeResources"))
    from(mergeLibraryDefinitions)
    into(appResources)
}

val generatedBuildInfo = layout.buildDirectory.dir("generated/buildInfo")
val appVersion = rootProject.extra["generatedVersionName"] as String
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val version = appVersion
    val output = generatedBuildInfo.get().file("info/anodsplace/evtimer/BuildInfo.kt").asFile
    inputs.property("appVersion", version)
    outputs.dir(generatedBuildInfo)
    doLast {
        output.parentFile.mkdirs()
        output.writeText("package info.anodsplace.evtimer\n\ninternal const val APP_VERSION = \"$version\"\n")
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "info.anodsplace.evtimer.generated.resources"
    customDirectory("commonMain", prepareAppResources.map { appResources.get() })
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
        commonMain { kotlin.srcDir(generateBuildInfo) }
        androidMain { kotlin.srcDir("src/jvmAndroidMain/kotlin") }
        jvmMain { kotlin.srcDir("src/jvmAndroidMain/kotlin") }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
        jvmMain.dependencies { implementation(libs.sqldelight.sqlite.driver) }
        commonMain.dependencies {
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
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

tasks.withType<Test>().configureEach {
    systemProperty("expectedAndroidActivityVersion", libs.versions.androidx.activity.get())
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}
