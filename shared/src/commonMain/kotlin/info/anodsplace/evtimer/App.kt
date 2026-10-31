package info.anodsplace.evtimer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import info.anodsplace.evtimer.ui.ChargingTimerApp
import info.anodsplace.evtimer.ui.NavigationViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(navigation: NavigationViewModel = koinViewModel()) {
    ChargingTimerApp(
        navigation = navigation,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    )
}
