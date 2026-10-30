package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.DesktopClockTimeFormatter
import info.anodsplace.evtimer.data.createDataStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<ClockTimeFormatter> { DesktopClockTimeFormatter() }
    single { createDataStore() }
}
