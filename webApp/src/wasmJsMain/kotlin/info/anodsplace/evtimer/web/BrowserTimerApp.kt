@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package info.anodsplace.evtimer.web

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import info.anodsplace.evtimer.data.*
import info.anodsplace.evtimer.ui.*
import info.anodsplace.evtimer.web.generated.resources.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.stringResource
import org.w3c.dom.events.Event
import kotlin.time.Clock

@JsFun("(epoch) => new Date(epoch).toLocaleTimeString(undefined, {hour:'numeric',minute:'2-digit'})")
private external fun localClock(epoch: Double): String

@JsFun("() => globalThis.evTimerPwa?.status() ?? 'unsupported'")
private external fun pwaStatus(): String

@JsFun("() => window.history.state?.evTimerDepth ?? 0")
private external fun historyDepth(): Int

@JsFun("(fragment, depth, push) => window.history[push ? 'pushState' : 'replaceState']({evTimerDepth:depth}, '', fragment)")
private external fun writeHistory(fragment: String, depth: Int, push: Boolean)

@Composable
internal fun BrowserTimerApp() {
    val storage = remember { BrowserStorage(IndexedDbStore()) }
    val runtime = remember { BrowserTimerRuntime(storage) }
    val scope = rememberCoroutineScope()
    val mutex = remember { Mutex() }
    val formatter = remember { ClockTimeFormatter { localClock(it.toDouble()) } }
    var state by remember { mutableStateOf<BrowserState?>(null) }
    var navigation by remember { mutableStateOf(AppNavigationState()) }
    var historyInitialized by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var notificationFailed by remember { mutableStateOf(false) }
    var offlineStatus by remember { mutableStateOf(pwaStatus()) }
    val completeTitle = stringResource(Res.string.web_notification_complete)
    val milestoneTitle = stringResource(Res.string.web_notification_milestone)
    val notificationBody = stringResource(Res.string.web_notification_body)
    val browser = remember(completeTitle, milestoneTitle, notificationBody) {
        BrowserNotificationBridge(completeTitle, milestoneTitle, notificationBody)
    }
    val notifications = remember(browser) { BrowserNotifications(storage, browser) }

    fun applyNavigation(next: AppNavigationState, push: Boolean = false) {
        val changed = next.current != navigation.current
        navigation = next
        next.current?.let {
            if (changed || !historyInitialized || window.location.hash != it.fragment()) {
                writeHistory(it.fragment(), if (push) historyDepth() + 1 else historyDepth(), push)
            }
        }
    }

    suspend fun refresh() {
        val loaded = runtime.reconcile()
        state = loaded
        now = Clock.System.now().toEpochMilliseconds()
        val entries = loaded.historyEntries()
        val ids = entries.map { it.id }.toSet()
        val pending = entries.firstOrNull()?.takeIf { !it.reviewed }?.id
        var next = navigation.reconcile(loaded.activeSession != null, pending, ids)
        if (!historyInitialized) {
            parseAppRoute(window.location.hash)?.let { next = next.restore(it, ids) }
            if (historyDepth() == 0 &&
                (next.current?.destination == AppDestination.Running || next.current?.completion == true)) {
                writeHistory(AppRoute(AppDestination.Config).fragment(), 0, false)
                writeHistory(next.current!!.fragment(), 1, true)
            } else writeHistory(next.current!!.fragment(), historyDepth(), false)
            historyInitialized = true
        }
        applyNavigation(next)
        if (!notificationFailed) {
            try {
                notifications.deliver()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                println("Browser notification: ${error.message}")
                notificationFailed = true
            }
        }
    }

    suspend fun operation(action: suspend () -> Unit = {}) = mutex.withLock {
        busy = true
        failed = false
        try {
            action()
            refresh()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("Browser timer: ${error.message}")
            failed = true
        } finally {
            busy = false
        }
    }

    fun navigate(destination: AppDestination) = applyNavigation(navigation.navigate(destination), push = true)

    fun back() {
        if (navigation.current?.completion == true) {
            scope.launch { operation { runtime.acknowledgeCompletion(navigation.current?.summaryId) } }
        } else if (historyDepth() > 0) window.history.back()
        else applyNavigation(navigation.back())
    }

    LaunchedEffect(runtime, notifications) {
        operation()
        while (true) {
            delay(1_000)
            if (browser.isForeground() && !failed) operation()
        }
    }
    DisposableEffect(runtime, notifications) {
        val pwaChanged: (Event) -> Unit = { offlineStatus = pwaStatus() }
        val resume: (Event) -> Unit = {
            if (browser.isForeground()) scope.launch { operation() }
        }
        val pop: (Event) -> Unit = {
            val requested = parseAppRoute(window.location.hash)
            scope.launch {
                operation {
                    if (navigation.current?.completion == true) {
                        runtime.acknowledgeCompletion(navigation.current?.summaryId)
                    }
                    refresh()
                    val ids = state!!.historyEntries().map { it.id }.toSet()
                    applyNavigation(requested?.let { navigation.restore(it, ids) } ?: navigation)
                }
            }
        }
        val notificationError: (Event) -> Unit = {
            println("Browser notification: browser reported delivery failure")
            notificationFailed = true
        }
        window.addEventListener("ev-timer-pwa-status", pwaChanged)
        document.addEventListener("visibilitychange", resume)
        window.addEventListener("pageshow", resume)
        window.addEventListener("focus", resume)
        window.addEventListener("popstate", pop)
        window.addEventListener("ev-timer-notification-error", notificationError)
        onDispose {
            window.removeEventListener("ev-timer-pwa-status", pwaChanged)
            document.removeEventListener("visibilitychange", resume)
            window.removeEventListener("pageshow", resume)
            window.removeEventListener("focus", resume)
            window.removeEventListener("popstate", pop)
            window.removeEventListener("ev-timer-notification-error", notificationError)
        }
    }

    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (notificationFailed) Text(stringResource(Res.string.web_notifications_error),
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                if (offlineStatus == "error" || offlineStatus == "update") {
                    Text(stringResource(if (offlineStatus == "error") Res.string.web_offline_error else Res.string.web_offline_update),
                        Modifier.padding(16.dp),
                        color = if (offlineStatus == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
                if (failed) {
                    Text(stringResource(Res.string.web_timer_error), Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { scope.launch { operation() } }) {
                        Text(stringResource(Res.string.web_storage_retry))
                    }
                }
                val loaded = state
                if (loaded == null || navigation.backStack.isEmpty()) {
                    if (!failed) CircularProgressIndicator(Modifier.padding(24.dp))
                } else {
                    NavDisplay(
                        backStack = navigation.backStack,
                        onBack = { back() },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                        entryProvider = { route ->
                            NavEntry(route) {
                                // NavDisplay retains entries; read observable state inside their content.
                                val loaded = requireNotNull(state)
                                val configuration = loaded.configuration()
                                when (route.destination) {
                                    AppDestination.Config -> ConfigurationScreen(
                                        ConfigurationViewState(false, configuration.settings, configuration.presets, configuration.selectedId),
                                        onEvent = { event ->
                                            scope.launch {
                                                operation {
                                                    if (event == ConfigurationViewEvent.StartCharging) runtime.start()
                                                    else runtime.configure(event)
                                                    if (event == ConfigurationViewEvent.StartCharging) {
                                                        applyNavigation(navigation.navigate(AppDestination.Running), push = true)
                                                    }
                                                }
                                            }
                                        },
                                        onOpenSettings = { navigate(AppDestination.Settings) }
                                    )
                                    AppDestination.Running -> loaded.runningViewState(now, formatter)?.let { running ->
                                        RunningTimerScreen(running, { event ->
                                            scope.launch {
                                                operation {
                                                    when (event) {
                                                        RunningViewEvent.StopCharging -> runtime.stop()
                                                        RunningViewEvent.UpdateCalculation -> runtime.reconcile()
                                                        is RunningViewEvent.ChangePower -> runtime.changePower(event.powerKw)
                                                        is RunningViewEvent.AddPower -> runtime.addPower(event.powerKw)
                                                    }
                                                }
                                            }
                                        })
                                    }
                                    AppDestination.Settings -> SettingsScreen(
                                        loaded.backup.preferences.notifyAt80,
                                        { enabled -> scope.launch { operation { runtime.setNotifyAt80(enabled) } } },
                                        onBack = ::back,
                                        onOpenLicenses = { navigate(AppDestination.Licenses) },
                                        onOpenPresets = { navigate(AppDestination.Presets) },
                                        onOpenHistory = { navigate(AppDestination.History) },
                                        onOpenChargingPowers = { navigate(AppDestination.ChargingPowers) },
                                        backupContent = {
                                            BrowserPreferences(loaded, offlineStatus, notificationFailed, busy) {
                                                // Permission must be requested inside trusted user activation.
                                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                    try {
                                                        if (state?.notificationsEnabled == true) notifications.disable()
                                                        else notifications.enable()
                                                        notificationFailed = false
                                                    } catch (error: CancellationException) {
                                                        throw error
                                                    } catch (error: Exception) {
                                                        println("Browser notification permission: ${error.message}")
                                                        notificationFailed = true
                                                    }
                                                    operation()
                                                }
                                            }
                                            BrowserBackupSettings(storage, loaded) { mutex.withLock { refresh() } }
                                        }
                                    )
                                    AppDestination.Presets -> PresetManagementScreen(
                                        configuration.presets,
                                        onRename = { id, name -> scope.launch { operation { runtime.renamePreset(id, name) } } },
                                        onDelete = { id -> scope.launch { operation { runtime.deletePreset(id) } } },
                                        onMove = { id, direction -> scope.launch { operation { runtime.movePreset(id, direction) } } },
                                        onReorder = { ids -> scope.launch { operation { runtime.reorderPresets(ids) } } },
                                        onBack = ::back,
                                        calibration = loaded.calibration(),
                                        onResetCalibration = { id, power -> mutex.withLock { runtime.resetLearnedEstimates(id, power); refresh() } }
                                    )
                                    AppDestination.ChargingPowers -> ChargingPowerManagementScreen(
                                        configuration.settings.availablePowers, configuration.settings.chargingPower,
                                        onAddPower = { power -> scope.launch { operation { runtime.addPower(power) } } },
                                        onRemovePower = { power -> scope.launch { operation { runtime.removePower(power) } } },
                                        onBack = ::back
                                    )
                                    AppDestination.History -> HistoryScreen(
                                        loaded.historyEntries(),
                                        onOpenSummary = { applyNavigation(navigation.openSummary(it.id), push = true) },
                                        onDelete = { id -> mutex.withLock { runtime.deleteHistory(id); refresh() } },
                                        onBack = ::back
                                    )
                                    AppDestination.EndSummary -> loaded.historyEntries().firstOrNull { it.id == route.summaryId }?.let { entry ->
                                        EndSummaryScreen(entry.summary(), onShare = ::shareSummary, onDone = ::back,
                                            entry = entry,
                                            onSaveReading = { id, actual, enabled ->
                                                mutex.withLock { runtime.saveReading(id, actual, enabled); refresh() }
                                            })
                                    }
                                    AppDestination.Licenses -> LicensesScreen(onBack = ::back)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserPreferences(
    state: BrowserState,
    offlineStatus: String,
    notificationFailed: Boolean,
    busy: Boolean,
    onPermission: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.web_browser_settings), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.web_foreground_limit), style = MaterialTheme.typography.bodyMedium)
            TextButton(enabled = !busy, onClick = onPermission) {
                Text(stringResource(if (state.notificationsEnabled)
                    Res.string.web_notifications_disable else Res.string.web_notifications_enable))
            }
            if (notificationFailed) Text(stringResource(Res.string.web_notifications_error),
                color = MaterialTheme.colorScheme.error)
            Text(stringResource(when (offlineStatus) {
                "ready" -> Res.string.web_offline_ready
                "update" -> Res.string.web_offline_update
                "error" -> Res.string.web_offline_error
                "unsupported" -> Res.string.web_offline_unsupported
                else -> Res.string.web_offline_loading
            }), color = if (offlineStatus == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}
