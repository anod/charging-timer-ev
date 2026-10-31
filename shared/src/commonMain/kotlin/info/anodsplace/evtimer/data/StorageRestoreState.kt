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
            check(cursor.next().value) { "Missing SQLite journal mode" }
            QueryResult.Value(cursor.getString(0))
        }, 0
    ).value
    check(mode == "delete") { "SQLite backup requires DELETE journal mode, got $mode" }
}
