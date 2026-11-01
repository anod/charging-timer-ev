package info.anodsplace.evtimer.ui

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import info.anodsplace.evtimer.data.ConfigurationViewModel
import info.anodsplace.evtimer.data.RunningViewModel
import info.anodsplace.evtimer.data.SettingsViewModel
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
                                    else ConfigurationScreen(
                                        viewState, viewModel::handleEvent,
                                        modifier.windowInsetsPadding(WindowInsets.systemBars),
                                        onOpenSettings = { navigation.navigate(Destination.Settings) }
                                    )
                                }
                                Destination.Running -> {
                                    val viewModel = koinViewModel<RunningViewModel>()
                                    val viewState by viewModel.viewStates.collectAsState()
                                    if (viewState.isLoading) Box(modifier.fillMaxSize())
                                    else RunningTimerScreen(viewState, viewModel::handleEvent, modifier)
                                }
                                Destination.Settings -> {
                                    val viewModel = koinViewModel<SettingsViewModel>()
                                    val notifyAt80 by viewModel.notifyAt80.collectAsState()
                                    val presets by viewModel.presets.collectAsState()
                                    SettingsScreen(
                                        notifyAt80, viewModel::setNotifyAt80,
                                        presets = presets,
                                        onRenamePreset = viewModel::renamePreset,
                                        onDeletePreset = viewModel::deletePreset,
                                        onMovePreset = viewModel::movePreset,
                                        onReorderPresets = viewModel::reorderPresets,
                                        onBack = { navigation.onBack() },
                                        onOpenLicenses = { navigation.navigate(Destination.Licenses) },
                                        modifier = modifier.windowInsetsPadding(WindowInsets.systemBars)
                                    )
                                }
                                Destination.Licenses -> LicensesScreen(
                                    onBack = { navigation.onBack() },
                                    modifier = modifier.windowInsetsPadding(WindowInsets.systemBars)
                                )
                                else -> error("Screen not implemented for $destination")
                            }
                        }
                    }
                )
            }
        }
    }
}
