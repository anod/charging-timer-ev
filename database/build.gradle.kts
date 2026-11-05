plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.android.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "info.anodsplace.evtimer.database"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { api(libs.sqldelight.runtime) }
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
