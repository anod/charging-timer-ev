package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ChargingRepository
import info.anodsplace.evtimer.data.ChargingRepositoryImpl
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingServiceImpl
import info.anodsplace.evtimer.data.ChargingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val platformModule: org.koin.core.module.Module

val commonModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    singleOf(::ChargingRepositoryImpl) {
        bind<ChargingRepository>()
    }
    singleOf(::ChargingServiceImpl) {
        bind<ChargingService>()
    }
}

val uiModule = module {
    viewModelOf(::ChargingViewModel)
}

fun appModules() = listOf(commonModule, platformModule, uiModule)