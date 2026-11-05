@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package info.anodsplace.evtimer.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import info.anodsplace.evtimer.data.*
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
    val viewport = document.body ?: error("Browser document body is unavailable")
    ComposeViewport(viewport) {
        BrowserEscapeNavigation(viewport)
        BrowserResources { BrowserTimerApp() }
    }
}

@Composable
internal fun BrowserBackupSettings(storage: BrowserStorage, state: BrowserState, onRestored: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
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

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.web_storage_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.web_storage_description))
            Text(stringResource(Res.string.web_storage_privacy))
            Text(stringResource(Res.string.web_storage_counts, state.backup.presets.size, state.backup.sessions.size))
            Button(enabled = !busy, onClick = {
                scope.launch {
                    operation {
                        val bytes = BackupFormat.encode(storage.exportBackup(WEB_APP_VERSION))
                        downloadBackup(bytes.decodeToString().toJsString())
                        downloadRequested = true
                    }
                }
            }) { Text(stringResource(Res.string.web_backup_export)) }
            Button(enabled = !busy && state.activeSession == null, onClick = {
                // File pickers need the browser's trusted click activation.
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    operation {
                        val selection = pickBackup()
                        val text = selection.await()?.toString()
                        if (text != null) pending = BackupFormat.decode(text.encodeToByteArray())
                    }
                }
            }) { Text(stringResource(Res.string.web_backup_import)) }
            if (busy) CircularProgressIndicator()
            if (failed) Text(stringResource(Res.string.web_storage_error), color = MaterialTheme.colorScheme.error)
            if (downloadRequested) Text(stringResource(Res.string.web_backup_download_requested))
        }
    }
    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text(stringResource(Res.string.web_backup_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.web_backup_confirm, backup.presets.size, backup.sessions.size))
                    if (failed) Text(stringResource(Res.string.web_storage_error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        operation {
                            storage.restoreBackup(backup)
                            onRestored()
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
