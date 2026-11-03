package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import info.anodsplace.evtimer.data.BackupState
import info.anodsplace.evtimer.data.SettingsViewModel
import info.anodsplace.evtimer.data.formatSessionDate
import info.anodsplace.evtimer.data.rememberBackupPicker
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
fun BackupSettings(viewModel: SettingsViewModel) {
    val picker = rememberBackupPicker(viewModel::exportBackup, viewModel::readBackup, viewModel::documentPickerError)
        ?: return
    val state by viewModel.backup.collectAsState()
    val session by viewModel.session.collectAsState()
    BackupSettingsContent(
        state, session?.isRunning,
        onExport = { picker.export(viewModel.backupFileName()) },
        onRestore = picker::restore,
        onConfirm = viewModel::confirmRestore,
        onDismiss = viewModel::dismissBackup
    )
}

@Composable
internal fun BackupSettingsContent(
    state: BackupState,
    isRunning: Boolean?,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SettingsSection(stringResource(Res.string.backup_title)) {
        SettingsRow(
            stringResource(Res.string.backup_export), Res.drawable.ic_backup_export,
            onClick = onExport, enabled = !state.busy
        )
        SettingsDivider()
        SettingsRow(
            stringResource(Res.string.backup_restore), Res.drawable.ic_backup_restore,
            supportingText = if (isRunning == true) stringResource(Res.string.backup_stop_first) else null,
            onClick = onRestore, enabled = !state.busy && isRunning == false
        )
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
            state.busy -> Res.string.backup_working
            else -> null
        }
        if (message != null) {
            SettingsDivider()
            Text(stringResource(message),
                Modifier.padding(start = 72.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.backup_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(
                        Res.string.backup_details,
                        formatSessionDate(Instant.parse(pending.exportedAt).toEpochMilliseconds()),
                        pending.appVersion,
                        pluralStringResource(Res.plurals.backup_preset_count, pending.presets.size, pending.presets.size),
                        pluralStringResource(Res.plurals.backup_session_count, pending.sessions.size, pending.sessions.size)
                    ))
                    Text(stringResource(Res.string.backup_replace_warning))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !state.busy && isRunning == false) {
                    Text(stringResource(Res.string.backup_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !state.busy) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}
