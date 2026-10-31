package info.anodsplace.evtimer.data

import android.content.Context

class AndroidStorageRestoreState(context: Context) : StorageRestoreState by FileStorageRestoreState(
    context.getDatabasePath("charging.db"), context.noBackupFilesDir
) {

    companion object {
        fun recordRestore(context: Context) {
            FileStorageRestoreState.recordRestore(context.noBackupFilesDir)
        }
    }
}
