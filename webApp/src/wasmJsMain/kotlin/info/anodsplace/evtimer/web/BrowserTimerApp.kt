@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package info.anodsplace.evtimer.web

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.*
import info.anodsplace.evtimer.ui.AppTheme
import info.anodsplace.evtimer.ui.ConfigurationScreen
import info.anodsplace.evtimer.ui.RunningTimerScreen
import info.anodsplace.evtimer.ui.HistoryScreen
import info.anodsplace.evtimer.ui.EndSummaryScreen
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

@Composable
internal fun BrowserTimerApp() {
    val storage = remember { BrowserStorage(IndexedDbStore()) }
    val runtime = remember { BrowserTimerRuntime(storage) }
    val scope = rememberCoroutineScope()
    val mutex = remember { Mutex() }
    val formatter = remember { ClockTimeFormatter { localClock(it.toDouble()) } }
    var state by remember { mutableStateOf<BrowserState?>(null) }
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    var storageOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var summaryId by remember { mutableStateOf<Long?>(null) }
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

    suspend fun refresh() {
        state = runtime.reconcile()
        now = Clock.System.now().toEpochMilliseconds()
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

    LaunchedEffect(runtime, notifications) {
        operation()
        while (true) {
            delay(1_000)
            if (browser.isForeground() && !failed) operation()
        }
    }
    DisposableEffect(runtime, notifications) {
        val pwaChanged: (Event) -> Unit = { offlineStatus = pwaStatus() }
        window.addEventListener("ev-timer-pwa-status", pwaChanged)
        val resume: (Event) -> Unit = {
            if (browser.isForeground()) scope.launch { operation() }
        }
        document.addEventListener("visibilitychange", resume)
        window.addEventListener("pageshow", resume)
        window.addEventListener("focus", resume)
        val notificationError: (Event) -> Unit = {
            println("Browser notification: browser reported delivery failure")
            notificationFailed = true
        }
        window.addEventListener("ev-timer-notification-error", notificationError)
        onDispose {
            window.removeEventListener("ev-timer-pwa-status", pwaChanged)
            document.removeEventListener("visibilitychange", resume)
            window.removeEventListener("pageshow", resume)
            window.removeEventListener("focus", resume)
            window.removeEventListener("ev-timer-notification-error", notificationError)
        }
    }

    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { storageOpen = !storageOpen; historyOpen = false; summaryId = null }) {
                        Text(stringResource(if (storageOpen) Res.string.web_timer_back else Res.string.web_storage_title))
                    }
                    TextButton(onClick = { historyOpen = !historyOpen; storageOpen = false; summaryId = null }) {
                        Text(stringResource(if (historyOpen) Res.string.web_timer_back else Res.string.web_history))
                    }
                    TextButton(enabled = !busy && state != null, onClick = {
                        // Permission must be requested before suspension, within trusted user activation.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                if (state?.notificationsEnabled == true) notifications.disable() else notifications.enable()
                                notificationFailed = false
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                println("Browser notification permission: ${error.message}")
                                notificationFailed = true
                            }
                            operation()
                        }
                    }) {
                        Text(stringResource(if (state?.notificationsEnabled == true)
                            Res.string.web_notifications_disable else Res.string.web_notifications_enable))
                    }
                }
                Text(stringResource(Res.string.web_foreground_limit),
                    modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(when (offlineStatus) {
                    "ready" -> Res.string.web_offline_ready
                    "update" -> Res.string.web_offline_update
                    "error" -> Res.string.web_offline_error
                    "unsupported" -> Res.string.web_offline_unsupported
                    else -> Res.string.web_offline_loading
                }), modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (offlineStatus == "error") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface)
                if (notificationFailed) {
                    Text(stringResource(Res.string.web_notifications_error),
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                }
                if (failed) {
                    Text(stringResource(Res.string.web_timer_error),
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { scope.launch { operation() } }) {
                        Text(stringResource(Res.string.web_storage_retry))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (storageOpen) BrowserStorageScreen(storage, state)
                    else state?.let { loaded ->
                        val entries = loaded.historyEntries()
                        val summaryEntry = entries.firstOrNull { it.id == summaryId }
                            ?: if (!historyOpen) entries.firstOrNull {
                                it.id == loaded.notificationSessionId && !it.reviewed
                            } else null
                        val running = loaded.runningViewState(now, formatter)
                        if (summaryEntry != null) {
                            EndSummaryScreen(summaryEntry.summary(), onShare = null, onDone = {
                                scope.launch {
                                    operation {
                                        if (summaryId == null) runtime.acknowledgeCompletion(summaryEntry.id)
                                        summaryId = null
                                    }
                                }
                            }, entry = summaryEntry, allowReadingEditing = false)
                        } else if (historyOpen) {
                            HistoryScreen(entries,
                                onOpenSummary = { summaryId = it.id },
                                onDelete = { id ->
                                    // Let HistoryScreen report failures; do not turn a failed write into success.
                                    mutex.withLock {
                                        runtime.deleteHistory(id)
                                        refresh()
                                    }
                                },
                                onBack = { historyOpen = false })
                        } else if (running != null) {
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
                        } else {
                            val configuration = loaded.configuration()
                            ConfigurationScreen(
                                ConfigurationViewState(false, configuration.settings, configuration.presets, configuration.selectedId),
                                onEvent = { event ->
                                    scope.launch {
                                        operation {
                                            if (event == ConfigurationViewEvent.StartCharging) runtime.start()
                                            else runtime.configure(event)
                                        }
                                    }
                                },
                                onOpenSettings = { storageOpen = true }
                            )
                        }
                    } ?: if (!failed) CircularProgressIndicator(Modifier.padding(24.dp)) else Unit
                }
            }
        }
    }
}
