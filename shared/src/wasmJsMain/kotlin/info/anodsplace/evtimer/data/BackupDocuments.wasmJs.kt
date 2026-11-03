package info.anodsplace.evtimer.data

import androidx.compose.runtime.Composable

@Composable
actual fun rememberBackupPicker(
    onExport: (BackupDestination) -> Unit,
    onRestore: (BackupSource) -> Unit,
    onError: () -> Unit
): BackupPicker? = null
