package info.anodsplace.evtimer

import androidx.compose.runtime.Composable
import info.anodsplace.evtimer.ui.ChargingTimerApp
import info.anodsplace.evtimer.ui.NavigationViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(navigation: NavigationViewModel = koinViewModel()) {
    ChargingTimerApp(navigation = navigation)
}
