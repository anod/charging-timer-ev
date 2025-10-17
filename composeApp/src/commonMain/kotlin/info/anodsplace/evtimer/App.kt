package info.anodsplace.evtimer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import info.anodsplace.evtimer.data.ChargingViewModel
import info.anodsplace.evtimer.ui.ChargingTimerApp
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinContext
import org.koin.compose.KoinMultiplatformApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration

@Composable
@Preview
fun App() {
    ChargingTimerApp(
        viewModel = koinViewModel<ChargingViewModel>(),
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    )
}
