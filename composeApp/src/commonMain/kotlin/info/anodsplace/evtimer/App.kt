package info.anodsplace.evtimer

import androidx.compose.runtime.Composable
import info.anodsplace.evtimer.data.ChargingViewModel
import info.anodsplace.evtimer.ui.ChargingTimerApp
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinMultiplatformApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration

@OptIn(KoinExperimentalAPI::class)
@Composable
@Preview
fun App() {
    KoinMultiplatformApplication(config = koinConfiguration {
        modules(appModules())
    }) {
        ChargingTimerApp(
            viewModel = koinViewModel<ChargingViewModel>(),
        )
    }
}
