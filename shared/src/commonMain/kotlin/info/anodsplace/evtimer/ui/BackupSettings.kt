package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.BackupError
import info.anodsplace.evtimer.data.BackupMessage
import info.anodsplace.evtimer.data.SettingsViewModel
import info.anodsplace.evtimer.data.rememberBackupPicker
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun BackupSettings(viewModel: SettingsViewModel) {
    val picker = rememberBackupPicker(viewModel::exportBackup, viewModel::readBackup, viewModel::documentPickerError)
        ?: return
    val state by viewModel.backup.collectAsState()
    val session by viewModel.session.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text(stringResource(Res.string.backup_title), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { picker.export(viewModel.backupFileName()) }, enabled = !state.busy) {
            Text(stringResource(Res.string.backup_export))
        }
        TextButton(onClick = picker::restore, enabled = !state.busy && session?.isRunning == false) {
            Text(stringResource(Res.string.backup_restore))
        }
        if (session?.isRunning == true) {
            Text(stringResource(Res.string.backup_stop_first), style = MaterialTheme.typography.bodySmall)
        }
        if (state.busy) Text(stringResource(Res.string.backup_working), style = MaterialTheme.typography.bodySmall)
        val message = when {
            state.error != null -> when (state.error) {
                BackupError.NEWER_VERSION -> Res.string.backup_newer_version
                BackupError.TOO_LARGE -> Res.string.backup_too_large
                BackupError.SESSION_RUNNING -> Res.string.backup_stop_first
                BackupError.INVALID_JSON -> Res.string.backup_invalid_json
                BackupError.INVALID_DATA -> Res.string.backup_invalid_data
                BackupError.PREFERENCES -> Res.string.backup_preferences_error
                else -> Res.string.backup_io_error
            }
            state.message == BackupMessage.EXPORTED -> Res.string.backup_exported
            state.message == BackupMessage.RESTORED -> Res.string.backup_restored
            else -> null
        }
        if (message != null) {
            Card(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(message), Modifier.padding(12.dp),
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackup,
            title = { Text(stringResource(Res.string.backup_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.backup_details, pending.exportedAt, pending.appVersion,
                        pending.presets.size, pending.sessions.size))
                    Text(stringResource(Res.string.backup_replace_warning))
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore, enabled = !state.busy && session?.isRunning == false) {
                    Text(stringResource(Res.string.backup_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBackup, enabled = !state.busy) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}
