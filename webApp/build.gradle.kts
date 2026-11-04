plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "info.anodsplace.evtimer.web.generated.resources"
}

val generatedBuildInfo = layout.buildDirectory.dir("generated/buildInfo")
val webVersion = rootProject.extra["generatedVersionName"] as String
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val version = webVersion
    val output = generatedBuildInfo.get().file("info/anodsplace/evtimer/web/BuildInfo.kt")
    inputs.property("version", version)
    outputs.dir(generatedBuildInfo)
    doLast {
        output.asFile.parentFile.mkdirs()
        output.asFile.writeText("package info.anodsplace.evtimer.web\n\ninternal const val WEB_APP_VERSION = \"$version\"\n")
    }
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "webApp"
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain { kotlin.srcDir(generateBuildInfo) }
        commonMain.dependencies {
            implementation(project(":storage"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
    }
}
