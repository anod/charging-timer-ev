package info.anodsplace.evtimer.ui

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import info.anodsplace.evtimer.data.ConfigurationViewModel
import info.anodsplace.evtimer.data.RunningViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChargingTimerApp(
    modifier: Modifier = Modifier,
    navigation: NavigationViewModel = koinViewModel()
) {
    val backStack by navigation.backStack.collectAsState()
    AppTheme {
        Surface {
            if (backStack.isEmpty()) {
                Box(modifier.fillMaxSize())
            } else {
            NavDisplay(
                backStack = backStack,
                onBack = { navigation.onBack() },
                modifier = Modifier.onPreviewKeyEvent {
                    it.type == KeyEventType.KeyUp && it.key == Key.Escape && navigation.onBack()
                },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = { destination ->
                    NavEntry(destination) {
                        when (destination) {
                            Destination.Config -> {
                                val viewModel = koinViewModel<ConfigurationViewModel>()
                                val viewState by viewModel.viewStates.collectAsState()
                                LaunchedEffect(viewModel) {
                                    viewModel.viewActions.collect {
                                        navigation.navigate(Destination.Running)
                                    }
                                }
                                if (viewState.isLoading) Box(modifier.fillMaxSize())
                                else ConfigurationScreen(viewState, viewModel::handleEvent, modifier)
                            }
                            Destination.Running -> {
                                val viewModel = koinViewModel<RunningViewModel>()
                                val viewState by viewModel.viewStates.collectAsState()
                                if (viewState.isLoading) Box(modifier.fillMaxSize())
                                else RunningTimerScreen(viewState, viewModel::handleEvent, modifier)
                            }
                            else -> error("Screen not implemented for $destination")
                        }
                    }
                }
            )
            }
        }
    }
}
