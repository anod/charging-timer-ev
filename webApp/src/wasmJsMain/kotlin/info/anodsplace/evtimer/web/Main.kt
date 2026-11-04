@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package info.anodsplace.evtimer.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import info.anodsplace.evtimer.data.*
import info.anodsplace.evtimer.ui.AppTheme
import info.anodsplace.evtimer.web.generated.resources.*
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.js.toJsString

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body ?: error("Browser document body is unavailable")) {
        BrowserResources { BrowserTimerApp() }
    }
}

@Composable
internal fun BrowserStorageScreen(storage: BrowserStorage, currentState: BrowserState?) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BrowserState?>(null) }
    var pending by remember { mutableStateOf<ManualBackup?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var downloadRequested by remember { mutableStateOf(false) }

    suspend fun operation(action: suspend () -> Unit) {
        busy = true
        failed = false
        downloadRequested = false
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("Browser storage: ${error.message}")
            failed = true
        } finally {
            busy = false
        }
    }

    LaunchedEffect(storage) { operation { state = storage.load() } }
    LaunchedEffect(currentState) {
        if (currentState != null) state = currentState
    }

    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(Res.string.web_storage_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(when {
                        state != null -> Res.string.web_storage_description
                        failed -> Res.string.web_storage_unavailable
                        else -> Res.string.web_storage_loading
                    }))
                    Text(stringResource(Res.string.web_storage_privacy))
                    state?.let { loaded ->
                        Text(stringResource(Res.string.web_storage_counts,
                            loaded.backup.presets.size, loaded.backup.sessions.size))
                        Button(enabled = !busy, onClick = {
                            scope.launch {
                                operation {
                                    val bytes = BackupFormat.encode(storage.exportBackup(WEB_APP_VERSION))
                                    downloadBackup(bytes.decodeToString().toJsString())
                                    downloadRequested = true
                                }
                            }
                        }) { Text(stringResource(Res.string.web_backup_export)) }
                        Button(enabled = !busy && loaded.activeSession == null, onClick = {
                            // File pickers need the browser's trusted click activation.
                            val selection = pickBackup()
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                operation {
                                    val text = selection.await()?.toString()
                                    if (text != null) pending = BackupFormat.decode(text.encodeToByteArray())
                                }
                            }
                        }) { Text(stringResource(Res.string.web_backup_import)) }
                    }
                    if (busy) CircularProgressIndicator()
                    if (failed) {
                        Text(stringResource(Res.string.web_storage_error), color = MaterialTheme.colorScheme.error)
                        TextButton(enabled = !busy, onClick = {
                            scope.launch { operation { state = storage.load() } }
                        }) { Text(stringResource(Res.string.web_storage_retry)) }
                    }
                    if (downloadRequested) Text(stringResource(Res.string.web_backup_download_requested))
                }
            }
        }
        pending?.let { backup ->
            AlertDialog(
                onDismissRequest = { if (!busy) pending = null },
                title = { Text(stringResource(Res.string.web_backup_confirm_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(Res.string.web_backup_confirm, backup.presets.size, backup.sessions.size))
                        if (failed) Text(stringResource(Res.string.web_storage_error),
                            color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    TextButton(enabled = !busy, onClick = {
                        scope.launch {
                            operation {
                                storage.restoreBackup(backup)
                                state = storage.load()
                                pending = null
                            }
                        }
                    }) { Text(stringResource(Res.string.web_backup_replace)) }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { pending = null }) {
                        Text(stringResource(Res.string.web_backup_cancel))
                    }
                }
            )
        }
    }
}
