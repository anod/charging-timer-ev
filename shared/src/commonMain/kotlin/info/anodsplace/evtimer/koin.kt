package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ChargingRepository
import info.anodsplace.evtimer.data.ChargingRepositoryImpl
import info.anodsplace.evtimer.data.PresetRepository
import info.anodsplace.evtimer.data.ChargingHistoryRepository
import info.anodsplace.evtimer.db.ChargingDatabase
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingServiceImpl
import info.anodsplace.evtimer.data.ConfigurationViewModel
import info.anodsplace.evtimer.data.RunningViewModel
import info.anodsplace.evtimer.data.SettingsViewModel
import info.anodsplace.evtimer.ui.NavigationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

expect val platformModule: org.koin.core.module.Module

@OptIn(ExperimentalTime::class)
val commonModule = module {
    single<Clock> { Clock.System }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    single { ChargingDatabase(get()) }
    singleOf(::ChargingRepositoryImpl) {
        bind<ChargingRepository>()
        bind<PresetRepository>()
        bind<ChargingHistoryRepository>()
    }
    singleOf(::ChargingServiceImpl) {
        bind<ChargingService>()
    }
}

val uiModule = module {
    viewModelOf(::ConfigurationViewModel)
    viewModelOf(::RunningViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::NavigationViewModel)
}

fun appModules() = listOf(commonModule, platformModule, uiModule)

fun initKoin(koinAppDeclaration: KoinAppDeclaration? = null) {
    startKoin {
        koinAppDeclaration?.invoke(this)
        modules(appModules())
    }
}