package info.anodsplace.evtimer.data

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
actual fun rememberBackupPicker(
    onExport: (BackupDestination) -> Unit,
    onRestore: (BackupSource) -> Unit,
    onError: () -> Unit
): BackupPicker? {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val exportCallback = rememberUpdatedState(onExport)
    val restoreCallback = rememberUpdatedState(onRestore)
    val errorCallback = rememberUpdatedState(onError)
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportCallback.value(BackupDestination { bytes ->
            withContext(Dispatchers.IO) {
                val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open backup for writing")
                stream.use { it.write(bytes) }
            }
        })
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreCallback.value(BackupSource {
            withContext(Dispatchers.IO) {
                val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot open backup for reading")
                stream.use {
                    // Bound allocation even when the provider reports no size or an incorrect size.
                    val bytes = it.readNBytes(BackupFormat.MAX_BYTES + 1)
                    if (bytes.size > BackupFormat.MAX_BYTES) {
                        throw BackupException(BackupError.TOO_LARGE, "Backup exceeds 16 MiB")
                    }
                    bytes
                }
            }
        })
    }
    return remember(create, open) {
        object : BackupPicker {
            private fun launch(action: () -> Unit) {
                try {
                    action()
                } catch (error: ActivityNotFoundException) {
                    Log.w("ManualBackup", "Document picker unavailable", error)
                    errorCallback.value()
                } catch (error: SecurityException) {
                    Log.w("ManualBackup", "Document access denied", error)
                    errorCallback.value()
                }
            }

            override fun export(suggestedName: String) = launch { create.launch(suggestedName) }
            override fun restore() = launch { open.launch(arrayOf("application/json", "application/octet-stream", "text/*")) }
        }
    }
}
