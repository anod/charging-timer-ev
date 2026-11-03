package info.anodsplace.evtimer.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

interface StorageRestoreState {
    val requiresInterruption: Boolean
    fun markReady()
}

object LocalStorageRestoreState : StorageRestoreState {
    override val requiresInterruption = false
    override fun markReady() = Unit
}

fun configureBackupJournal(driver: SqlDriver) {
    val mode = driver.executeQuery(
        null, "PRAGMA journal_mode = DELETE",
        { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
        }, 0
    ).value
    if (mode != "delete") {
        println("Charging storage: SQLite retained journal mode $mode; backup includes WAL for recovery")
    }
}
