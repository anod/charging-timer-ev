package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.DesktopClockTimeFormatter
import info.anodsplace.evtimer.data.createDataStore
import org.koin.core.module.Module
import org.koin.dsl.module
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase
import java.io.File
import java.util.Properties

actual val platformModule: Module = module {
    single<SqlDriver> {
        val os = System.getProperty("os.name").lowercase()
        val root = when {
            os.contains("win") -> File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"))
            os.contains("mac") -> File(File(System.getProperty("user.home"), "Library"), "Application Support")
            else -> File(System.getenv("XDG_DATA_HOME") ?: File(File(System.getProperty("user.home"), ".local"), "share").path)
        }
        val directory = File(root, "charging-timer-ev")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create app data directory: $directory" }
        JdbcSqliteDriver(
            "jdbc:sqlite:${File(directory, "charging.db").absolutePath}",
            Properties().apply { setProperty("foreign_keys", "true") },
            ChargingDatabase.Schema
        )
    }
    single<ClockTimeFormatter> { DesktopClockTimeFormatter() }
    single { createDataStore() }
}
