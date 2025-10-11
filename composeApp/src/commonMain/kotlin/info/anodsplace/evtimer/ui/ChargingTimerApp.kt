package info.anodsplace.evtimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import info.anodsplace.evtimer.data.ChargingViewModel

@Composable
fun ChargingTimerApp(
    viewModel: ChargingViewModel,
    modifier: Modifier = Modifier
) {
    val viewState by viewModel.viewStates.collectAsState()
    AppTheme {
        if (viewState.isRunning) {
            RunningTimerScreen(
                viewState = viewState,
                onEvent = viewModel::handleEvent,
                modifier = modifier
            )
        } else {
            ConfigurationScreen(
                viewState = viewState,
                onEvent = viewModel::handleEvent,
                modifier = modifier
            )
        }
    }
}
