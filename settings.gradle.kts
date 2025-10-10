rootProject.name = "charging-timer-ev"

include(":shared")
include(":desktopApp")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
