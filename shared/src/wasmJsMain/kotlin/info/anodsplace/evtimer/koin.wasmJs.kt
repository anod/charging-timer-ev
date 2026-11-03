package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.LocalStorageRestoreState
import info.anodsplace.evtimer.data.ShareService
import info.anodsplace.evtimer.data.StorageRestoreState
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { ShareService() }
    single<StorageRestoreState> { LocalStorageRestoreState }
    single<ClockTimeFormatter> { WebClockTimeFormatter() }
}

actual val storageModule: Module = module {}

private class WebClockTimeFormatter : ClockTimeFormatter {
    override fun format(epochMilliseconds: Long): String =
        js("new Date(Number(epochMilliseconds)).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })")
}
