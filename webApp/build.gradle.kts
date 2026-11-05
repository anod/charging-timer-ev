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

    abstract class PwaProcess {
        @get:javax.inject.Inject
        abstract val execOperations: org.gradle.process.ExecOperations
    }

    tasks.named("wasmJsBrowserDistribution") {
        val process = objects.newInstance<PwaProcess>()
        val script = layout.projectDirectory.file("pwa/prepare.cjs").asFile.absolutePath
        val distribution = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
        inputs.files(fileTree("pwa"))
        doLast {
            process.execOperations.exec {
                commandLine("node", script, distribution.get().asFile.absolutePath)
            }.assertNormalExitValue()
        }
    }

    sourceSets {
        commonMain { kotlin.srcDir(generateBuildInfo) }
        commonMain.dependencies {
            implementation(project(":storage"))
            implementation(project(":shared"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.androidx.navigation3.ui)
        }
    }
}
