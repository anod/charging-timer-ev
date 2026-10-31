package info.anodsplace.evtimer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import info.anodsplace.evtimer.ui.ChargingTimerApp

@Composable
fun App() {
    ChargingTimerApp(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    )
}
