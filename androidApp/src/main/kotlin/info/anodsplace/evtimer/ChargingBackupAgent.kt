package info.anodsplace.evtimer

import android.app.backup.BackupAgentHelper
import info.anodsplace.evtimer.data.AndroidStorageRestoreState

class ChargingBackupAgent : BackupAgentHelper() {
    override fun onRestoreFinished() {
        super.onRestoreFinished()
        AndroidStorageRestoreState.recordRestore(this)
    }
}
