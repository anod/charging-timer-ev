package info.anodsplace.evtimer.data

import android.content.Context
import java.io.File

class AndroidStorageRestoreState(context: Context) : StorageRestoreState {
    private val installation = File(context.noBackupFilesDir, "charging_storage_installation")
    private val restored = File(context.noBackupFilesDir, "charging_restore_pending")

    // Capture before the driver creates/opens the database; neither marker is backed up.
    override val requiresInterruption = restored.exists() ||
        (context.getDatabasePath("charging.db").exists() && !installation.exists())

    override fun markReady() {
        check(installation.isFile || installation.createNewFile()) { "Cannot mark local charging storage" }
        check(!restored.exists() || restored.delete()) { "Cannot clear charging restore marker" }
    }

    companion object {
        fun recordRestore(context: Context) {
            val marker = File(context.noBackupFilesDir, "charging_restore_pending")
            check(marker.isFile || marker.createNewFile()) { "Cannot record charging storage restore" }
        }
    }
}
