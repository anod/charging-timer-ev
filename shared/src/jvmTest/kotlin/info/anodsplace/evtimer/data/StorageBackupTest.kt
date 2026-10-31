package info.anodsplace.evtimer.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageBackupTest {
    @Test
    fun unsupportedJournalModeDoesNotPreventStartup() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = ChargingDatabase.Schema).use {
            configureBackupJournal(it)
            ChargingDatabase(it).storageQueries.putPreset("custom", "Custom", 83.0, 11.0, 100.0)
            assertEquals("Custom", ChargingDatabase(it).storageQueries.presetById("custom").executeAsOne().name)
        }
    }

    @Test
    fun fallbackWalBackupRetainsCommittedRowsWithoutSharedMemoryFile() {
        val directory = Files.createTempDirectory("evtimer-wal-backup").toFile()
        val source = File(directory, "source.db")
        val backup = File(directory, "backup.db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${source.path}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("PRAGMA journal_mode=WAL")
                    statement.execute("PRAGMA wal_autocheckpoint=0")
                    statement.execute("CREATE TABLE payload(value INTEGER)")
                    statement.execute("INSERT INTO payload VALUES(42)")
                    source.copyTo(backup)
                    File("${source.path}-wal").copyTo(File("${backup.path}-wal"))
                }
            }
            assertFalse(File("${backup.path}-shm").exists())
            DriverManager.getConnection("jdbc:sqlite:${backup.path}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT value FROM payload").use {
                        assertTrue(it.next())
                        assertEquals(42, it.getInt(1))
                    }
                }
            }
        } finally {
            directory.listFiles()!!.forEach { check(it.delete()) }
            check(directory.delete())
        }
    }

    @Test
    fun committedRowsAreInMainDatabaseWithDeleteJournal() {
        val directory = Files.createTempDirectory("evtimer-backup").toFile()
        val source = File(directory, "source.db")
        val backup = File(directory, "backup.db")
        try {
            JdbcSqliteDriver("jdbc:sqlite:${source.path}", schema = ChargingDatabase.Schema).use { driver ->
                configureBackupJournal(driver)
                ChargingDatabase(driver).storageQueries.putPreset("custom", "Custom", 83.0, 11.0, 100.0)
                DriverManager.getConnection("jdbc:sqlite:${source.path}").use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("PRAGMA journal_mode").use {
                            assertTrue(it.next())
                            assertEquals("delete", it.getString(1))
                        }
                    }
                }
                assertFalse(File("${source.path}-wal").exists())
                source.copyTo(backup)
            }
            JdbcSqliteDriver("jdbc:sqlite:${backup.path}", schema = ChargingDatabase.Schema).use {
                assertEquals("Custom", ChargingDatabase(it).storageQueries.presetById("custom").executeAsOne().name)
            }
        } finally {
            directory.listFiles()!!.forEach { check(it.delete()) }
            check(directory.delete())
        }
    }

    @Test
    fun includedRollbackJournalRecoversBackupTakenAfterWriterIsStopped() {
        val directory = Files.createTempDirectory("evtimer-hot-journal").toFile()
        val source = File(directory, "source.db")
        val backup = File(directory, "backup.db")
        try {
            JdbcSqliteDriver("jdbc:sqlite:${source.path}").use { driver -> configureBackupJournal(driver) }
            DriverManager.getConnection("jdbc:sqlite:${source.path}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("PRAGMA cache_size=5")
                    statement.execute("PRAGMA synchronous=FULL")
                    statement.execute("CREATE TABLE payload(id INTEGER PRIMARY KEY, value BLOB)")
                    statement.execute("INSERT INTO payload VALUES(1, zeroblob(65536))")
                    connection.autoCommit = false
                    statement.execute("UPDATE payload SET value=randomblob(65536) WHERE id=1")
                    val journal = File("${source.path}-journal")
                    assertTrue(journal.length() > 0)
                    // Copy both files with the writer paused; the copy has no live writer/SQLite locks.
                    source.copyTo(backup)
                    journal.copyTo(File("${backup.path}-journal"))
                    connection.rollback()
                }
            }
            DriverManager.getConnection("jdbc:sqlite:${backup.path}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT value=zeroblob(65536) FROM payload WHERE id=1").use {
                        assertTrue(it.next())
                        assertEquals(1, it.getInt(1))
                    }
                    statement.executeQuery("PRAGMA integrity_check").use {
                        assertTrue(it.next())
                        assertEquals("ok", it.getString(1))
                    }
                }
            }
        } finally {
            directory.listFiles()!!.forEach { check(it.delete()) }
            check(directory.delete())
        }
    }

    @Test
    fun committedV1BackupMigratesOnOpenAndKeepsExistingRows() {
        val file = Files.createTempFile("evtimer-v1-restore", ".db").toFile()
        try {
            File("src/commonMain/sqldelight/databases/1.db").copyTo(file, overwrite = true)
            DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
                connection.createStatement().use {
                    it.execute("PRAGMA user_version=1")
                    it.execute("INSERT INTO preset VALUES('custom','Custom',83,11,100)")
                    it.execute("INSERT INTO charge_session(id,battery_capacity,start_percent,target_percent,efficiency,is_running) VALUES(123,83,60,100,1,1)")
                    it.execute("INSERT INTO power_segment VALUES(123,123,11)")
                    it.execute("INSERT INTO storage_migration VALUES(1,123)")
                }
            }
            JdbcSqliteDriver("jdbc:sqlite:${file.path}", schema = ChargingDatabase.Schema).use { driver ->
                val row = ChargingDatabase(driver).storageQueries.activeSession().executeAsOne()
                assertEquals(123L, row.id)
                assertFalse(row.interrupted)
                assertFalse(row.preset_edited)
                assertTrue(row.is_running)
            }
        } finally {
            check(file.delete())
        }

    }

    @Test
    fun v2BackupMigratesAndKeepsPresetSelectionDataAndSessionIdentity() {
        val file = Files.createTempFile("evtimer-v2-restore", ".db").toFile()
        try {
            File("src/commonMain/sqldelight/databases/2.db").copyTo(file, overwrite = true)
            DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
                connection.createStatement().use {
                    it.execute("PRAGMA user_version=2")
                    it.execute("INSERT INTO preset VALUES('custom','Custom',83,11,100)")
                    it.execute("INSERT INTO preset VALUES('home','Home',60,7,80)")
                    it.execute("INSERT INTO charge_session(id,battery_capacity,start_percent,target_percent,efficiency,preset_id,preset_name,is_running) VALUES(123,60,25,80,1,'home','Home',1)")
                    it.execute("INSERT INTO power_segment VALUES(123,123,7)")
                    it.execute("INSERT INTO storage_migration VALUES(1,123)")
                }
            }
            JdbcSqliteDriver("jdbc:sqlite:${file.path}", schema = ChargingDatabase.Schema).use { driver ->
                val queries = ChargingDatabase(driver).storageQueries
                assertEquals(2, queries.allPresets().executeAsList().size)
                val session = queries.activeSession().executeAsOne()
                assertEquals("home", session.preset_id)
                assertEquals("Home", session.preset_name)
                assertFalse(session.preset_edited)
                assertTrue(session.is_running)
            }
        } finally {
            check(file.delete())
        }
    }
}
