package info.anodsplace.evtimer.data

import androidx.compose.runtime.Composable

fun interface BackupSource {
    suspend fun read(): ByteArray
}

fun interface BackupDestination {
    suspend fun write(bytes: ByteArray)
}

interface BackupPicker {
    fun export(suggestedName: String)
    fun restore()
}

@Composable
expect fun rememberBackupPicker(
    onExport: (BackupDestination) -> Unit,
    onRestore: (BackupSource) -> Unit,
    onError: () -> Unit
): BackupPicker?
