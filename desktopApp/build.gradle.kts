import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

val generatedDesktopVersion = rootProject.extra["generatedDesktopVersion"] as String

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.koin.compose.viewmodel)
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
