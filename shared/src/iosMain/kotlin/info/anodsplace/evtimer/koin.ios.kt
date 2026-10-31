package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.IosClockTimeFormatter
import info.anodsplace.evtimer.data.createDataStore
import org.koin.core.module.Module
import org.koin.dsl.module
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase
import info.anodsplace.evtimer.data.LocalStorageRestoreState
import info.anodsplace.evtimer.data.StorageRestoreState

actual val platformModule: Module = module {
    single<StorageRestoreState> { LocalStorageRestoreState }
    single<SqlDriver> {
        NativeSqliteDriver(ChargingDatabase.Schema, "charging.db").also {
            it.execute(null, "PRAGMA foreign_keys = ON", 0)
        }
    }
    single<ClockTimeFormatter> { IosClockTimeFormatter() }
    single { createDataStore() }
}
