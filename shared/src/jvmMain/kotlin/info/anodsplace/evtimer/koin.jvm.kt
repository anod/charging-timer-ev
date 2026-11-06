package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.ChargingHistoryRepository
import info.anodsplace.evtimer.data.ChargingRepository
import info.anodsplace.evtimer.data.ChargingRepositoryImpl
import info.anodsplace.evtimer.data.DesktopClockTimeFormatter
import info.anodsplace.evtimer.data.desktopAppDataDirectory
import info.anodsplace.evtimer.data.ManualBackupRepository
import info.anodsplace.evtimer.data.PresetRepository
import info.anodsplace.evtimer.data.createDataStore
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase
import java.io.File
import java.util.Properties
import info.anodsplace.evtimer.data.LocalStorageRestoreState
import info.anodsplace.evtimer.data.StorageRestoreState
import info.anodsplace.evtimer.data.ensurePrivateAppDataDirectory

actual val platformModule: Module = module {
    single { info.anodsplace.evtimer.data.ShareService() }
    single<StorageRestoreState> { LocalStorageRestoreState }
    single<SqlDriver> {
        val directory = ensurePrivateAppDataDirectory(desktopAppDataDirectory().toPath())
        JdbcSqliteDriver(
            "jdbc:sqlite:${File(directory.toFile(), "charging.db").absolutePath}",
            Properties().apply { setProperty("foreign_keys", "true") },
            ChargingDatabase.Schema
        )
    }
    single<ClockTimeFormatter> { DesktopClockTimeFormatter() }
    single { createDataStore() }
}

actual val storageModule: Module = module {
    single { ChargingDatabase(get()) }
    singleOf(::ChargingRepositoryImpl) {
        bind<ChargingRepository>()
        bind<PresetRepository>()
        bind<ChargingHistoryRepository>()
        bind<ManualBackupRepository>()
    }
}
