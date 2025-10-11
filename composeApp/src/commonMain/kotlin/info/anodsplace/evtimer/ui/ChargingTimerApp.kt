package info.anodsplace.evtimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import info.anodsplace.evtimer.data.ChargingViewModel

@Composable
fun ChargingTimerApp(
    viewModel: ChargingViewModel,
    modifier: Modifier = Modifier
) {
    AppTheme {
        if (viewModel.state.isRunning) {
            RunningTimerScreen(
                viewModel = viewModel,
                onStopCharging = { viewModel.stopCharging() },
                modifier = modifier
            )
        } else {
            ConfigurationScreen(
                viewModel = viewModel,
                onStartCharging = { viewModel.startCharging() },
                modifier = modifier
            )
        }
    }
}
