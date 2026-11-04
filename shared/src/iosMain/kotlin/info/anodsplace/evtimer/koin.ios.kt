package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.ChargingHistoryRepository
import info.anodsplace.evtimer.data.ChargingRepository
import info.anodsplace.evtimer.data.ChargingRepositoryImpl
import info.anodsplace.evtimer.data.ManualBackupRepository
import info.anodsplace.evtimer.data.PresetRepository
import info.anodsplace.evtimer.data.IosClockTimeFormatter
import info.anodsplace.evtimer.data.createDataStore
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase
import info.anodsplace.evtimer.data.LocalStorageRestoreState
import info.anodsplace.evtimer.data.StorageRestoreState

actual val platformModule: Module = module {
    single { info.anodsplace.evtimer.data.ShareService() }
    single<StorageRestoreState> { LocalStorageRestoreState }
    single<SqlDriver> {
        NativeSqliteDriver(ChargingDatabase.Schema, "charging.db").also {
            it.execute(null, "PRAGMA foreign_keys = ON", 0)
        }
    }
    single<ClockTimeFormatter> { IosClockTimeFormatter() }
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
