package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.AndroidClockTimeFormatter
import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.createDataStore
import org.koin.core.module.Module
import org.koin.dsl.module
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase
import info.anodsplace.evtimer.data.AndroidStorageRestoreState
import info.anodsplace.evtimer.data.StorageRestoreState
import info.anodsplace.evtimer.data.configureBackupJournal
import android.database.sqlite.SQLiteException
import android.util.Log

actual val platformModule: Module = module {
    single<StorageRestoreState> { AndroidStorageRestoreState(get()) }
    single<SqlDriver> {
        get<StorageRestoreState>()
        AndroidSqliteDriver(ChargingDatabase.Schema, get(), "charging.db").also {
            try {
                configureBackupJournal(it)
            } catch (error: SQLiteException) {
                Log.w("ChargingStorage", "Cannot change journal mode; backup includes WAL for recovery", error)
            }
            it.execute(null, "PRAGMA foreign_keys = ON", 0)
        }
    }
    single<ClockTimeFormatter> { AndroidClockTimeFormatter(context = get()) }
    single { createDataStore(context = get()) }
}
