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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import info.anodsplace.evtimer.data.ConfigurationViewModel
import info.anodsplace.evtimer.data.RunningViewModel
import info.anodsplace.evtimer.data.SettingsViewModel
import info.anodsplace.evtimer.data.HistoryViewModel
import info.anodsplace.evtimer.data.ShareService
import info.anodsplace.evtimer.data.summary
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal class OpaqueBackgroundNavEntryDecorator : NavEntryDecorator<Destination>(
    decorate = { entry ->
        // The background must transform with its entry, not remain behind both animated scenes.
        Surface(Modifier.fillMaxSize()) {
            entry.Content()
        }
    }
)

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
                        rememberViewModelStoreNavEntryDecorator(),
                        remember { OpaqueBackgroundNavEntryDecorator() }
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
                                    SettingsScreen(
                                        notifyAt80, viewModel::setNotifyAt80,
                                        onOpenPresets = { navigation.navigate(Destination.Presets) },
                                        onOpenChargingPowers = { navigation.navigate(Destination.ChargingPowers) },
                                        onBack = { navigation.onBack() },
                                        onOpenLicenses = { navigation.navigate(Destination.Licenses) },
                                        backupContent = { BackupSettings(viewModel) },
                                        onOpenHistory = { navigation.navigate(Destination.History) },
                                        modifier = modifier.windowInsetsPadding(WindowInsets.systemBars)
                                    )
                                }
                                Destination.ChargingPowers -> {
                                    val viewModel = koinViewModel<SettingsViewModel>()
                                    val settings by viewModel.settings.collectAsState()
                                    val loaded = settings
                                    if (loaded == null) Box(modifier.fillMaxSize())
                                    else ChargingPowerManagementScreen(
                                        powers = loaded.availablePowers,
                                        selectedPower = loaded.chargingPower,
                                        onAddPower = viewModel::addPower,
                                        onRemovePower = viewModel::removePowerForUndo,
                                        onUndoRemovePower = viewModel::restorePower,
                                        onBack = { navigation.onBack() },
                                        modifier = modifier.windowInsetsPadding(WindowInsets.systemBars)
                                    )
                                }
                                Destination.Presets -> {
                                    val viewModel = koinViewModel<SettingsViewModel>()
                                    val presets by viewModel.presets.collectAsState()
                                    val calibration by viewModel.calibration.collectAsState()
                                    PresetManagementScreen(
                                        presets, viewModel::renamePreset, viewModel::deletePreset,
                                        viewModel::movePreset, viewModel::reorderPresets,
                                        onBack = { navigation.onBack() },
                                        calibration = calibration,
                                        onResetCalibration = viewModel::resetLearnedEstimates,
                                        modifier = modifier.windowInsetsPadding(WindowInsets.systemBars)
                                    )
                                }
                                Destination.History -> {
                                    val viewModel = koinViewModel<HistoryViewModel>()
                                    val entries by viewModel.entries.collectAsState()
                                    HistoryScreen(
                                        entries = entries,
                                        onOpenSummary = navigation::openHistorySummary,
                                        onDelete = viewModel::deleteSession,
                                        onBack = { navigation.onBack() },
                                        modifier = modifier.windowInsetsPadding(WindowInsets.systemBars)
                                    )
                                }
                                Destination.EndSummary -> {
                                    val entry by navigation.summary.collectAsState()
                                    entry?.let {
                                        val shareService = koinInject<ShareService>()
                                        EndSummaryScreen(
                                            summary = it.summary(),
                                            onShare = shareService::share,
                                            onDone = { navigation.onBack() },
                                            entry = it,
                                            onSaveReading = navigation::saveReading,
                                            modifier = modifier.windowInsetsPadding(WindowInsets.systemBars)
                                        )
                                    }
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
