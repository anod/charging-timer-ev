package info.anodsplace.evtimer.data

import java.io.File

class FileStorageRestoreState(
    private val database: File,
    private val noBackupDirectory: File
) : StorageRestoreState {
    private val installation = File(noBackupDirectory, "charging_storage_installation")
    private val restored = File(noBackupDirectory, "charging_restore_pending")

    init {
        // Seed before a fresh/legacy database is created, not after its session import.
        if (!database.exists()) markInstallation()
    }

    override val requiresInterruption: Boolean
        get() = restored.exists() || (database.exists() && !installation.exists())

    private fun markInstallation() {
        check(installation.isFile || installation.createNewFile()) { "Cannot mark local charging storage" }
    }

    override fun markReady() {
        markInstallation()
        check(!restored.exists() || restored.delete()) { "Cannot clear charging restore marker" }
    }

    companion object {
        fun recordRestore(noBackupDirectory: File) {
            val marker = File(noBackupDirectory, "charging_restore_pending")
            check(marker.isFile || marker.createNewFile()) { "Cannot record charging storage restore" }
        }
    }
}
