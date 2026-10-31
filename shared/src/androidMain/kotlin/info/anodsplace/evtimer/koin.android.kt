package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.AndroidClockTimeFormatter
import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.createDataStore
import org.koin.core.module.Module
import org.koin.dsl.module
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase

actual val platformModule: Module = module {
    single<SqlDriver> {
        AndroidSqliteDriver(ChargingDatabase.Schema, get(), "charging.db").also {
            it.execute(null, "PRAGMA foreign_keys = ON", 0)
        }
    }
    single<ClockTimeFormatter> { AndroidClockTimeFormatter(context = get()) }
    single { createDataStore(context = get()) }
}
