package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.AndroidClockTimeFormatter
import info.anodsplace.evtimer.data.ChargingHistoryRepository
import info.anodsplace.evtimer.data.ChargingRepository
import info.anodsplace.evtimer.data.ChargingRepositoryImpl
import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.ManualBackupRepository
import info.anodsplace.evtimer.data.PresetRepository
import info.anodsplace.evtimer.data.createDataStore
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
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
    single { info.anodsplace.evtimer.data.ShareService(get()) }
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

actual val storageModule: Module = module {
    single { ChargingDatabase(get()) }
    singleOf(::ChargingRepositoryImpl) {
        bind<ChargingRepository>()
        bind<PresetRepository>()
        bind<ChargingHistoryRepository>()
        bind<ManualBackupRepository>()
    }
}
