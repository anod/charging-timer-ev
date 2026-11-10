package info.anodsplace.evtimer.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class ChargingPersistenceTest {
    @Test
    fun legacyUpgradeAndProcessRestartPreserveSessionAndEditedSettings() = runTest {
        val file = Files.createTempFile("evtimer-session", ".preferences_pb").toFile()
        val oldJob = Job()
        val newJob = Job()
        val restartJob = Job()
        val dbFile = Files.createTempFile("evtimer-session", ".db").toFile()
        var driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", schema = ChargingDatabase.Schema)
        try {
            val oldStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + oldJob), produceFile = { file }
            )
            oldStore.edit {
                it[SessionPreferences.isRunning] = true
                it[SessionPreferences.startTime] = 1_800_000_000_123L
                it[SessionPreferences.batteryCapacity] = 60f
                it[SessionPreferences.chargingPower] = 12f
                it[SessionPreferences.startPercent] = 20f
                it[SessionPreferences.maxPercent] = 80f
            }
            oldJob.cancelAndJoin()
            val upgraded = PreferenceDataStoreFactory.create(
                migrations = listOf(ChargingSessionMigration()),
                scope = CoroutineScope(Dispatchers.IO + newJob), produceFile = { file }
            )
            val repo = ChargingRepositoryImpl(upgraded, ChargingDatabase(driver), Clock.System, LocalStorageRestoreState)
            val session = repo.observeSession().first()
            assertTrue(session.isRunning)
            assertEquals(1_800_000_000_123L, session.startTime)
            val expected = ChargingSessionSnapshot.from(ChargingSettings(60f, 12f, 20f, 80f), session.startTime)
            assertEquals(expected, session.snapshot)
            val newSettings = ChargingSettings(100f, 50f, 10f, 90f)
            repo.saveSettings(newSettings)
            newJob.cancelAndJoin()
            driver.close()
            driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", schema = ChargingDatabase.Schema)
            val restarted = PreferenceDataStoreFactory.create(
                migrations = listOf(ChargingSessionMigration()),
                scope = CoroutineScope(Dispatchers.IO + restartJob), produceFile = { file }
            )
            val restored = ChargingRepositoryImpl(restarted, ChargingDatabase(driver), Clock.System, LocalStorageRestoreState)
            assertEquals(session, restored.observeSession().first())
            assertEquals(newSettings, restored.observeSettings().first())
            val segmented = expected.copy(
                presetId = "ev", presetName = "My EV", efficiency = 0.9f,
                segments = expected.segments + PowerSegment(expected.startTime + 60_000L, 24f),
                taperStartPercent = 75f, taperEndPercent = 95f, taperPowerCapKw = 18f
            )
            restored.saveSession(ChargingSession(true, segmented))
            assertEquals(segmented, restored.observeSession().first().snapshot)
            restored.saveSession(ChargingSession(false, segmented))
            assertFalse(restored.observeSession().first().isRunning)
            assertEquals(segmented, restored.observeSession().first().snapshot)
        } finally {
            oldJob.cancelAndJoin()
            newJob.cancelAndJoin()
            restartJob.cancelAndJoin()
            driver.close()
            check(file.delete())
            check(dbFile.delete())
        }
    }

    @Test
    fun versionFiveDatabaseMigrationAddsDefaultTaperSettingsToPresetsAndHistory() {
        val source = File("../database/src/commonMain/sqldelight/databases/4.db")
        val dbFile = Files.createTempFile("evtimer-schema-v4", ".db").toFile()
        Files.copy(source.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        try {
            ChargingDatabase.Schema.migrate(driver, 4L, 5L)
            driver.execute(null,
                "INSERT INTO preset(id, name, battery_capacity, charging_power, target_percent, position) " +
                    "VALUES ('custom', 'Default', 60, 7, 90, 0)", 0)
            driver.execute(null,
                "INSERT INTO preset(id, name, battery_capacity, charging_power, target_percent, position) " +
                    "VALUES ('legacy', 'Legacy', 60, 7, 90, 1)", 0)
            driver.execute(null,
                "INSERT INTO charge_session(id, battery_capacity, start_percent, target_percent, efficiency, ended_at, end_percent) " +
                    "VALUES (1, 60, 20, 80, 1, 100, 80)", 0)
            ChargingDatabase.Schema.migrate(driver, 5L, 6L)
            val database = ChargingDatabase(driver)
            val custom = database.storageQueries.presetById("custom").executeAsOne()
            val preset = database.storageQueries.presetById("legacy").executeAsOne()
            val history = database.storageQueries.history().executeAsList().single()

            assertEquals(80.0, custom.taper_start_percent)
            assertEquals(100.0, custom.taper_end_percent)
            assertEquals(22.0, custom.taper_power_cap_kw)
            assertEquals(80.0, preset.taper_start_percent)
            assertEquals(100.0, preset.taper_end_percent)
            assertEquals(22.0, preset.taper_power_cap_kw)
            assertEquals(80.0, history.taper_start_percent)
            assertEquals(100.0, history.taper_end_percent)
            assertEquals(22.0, history.taper_power_cap_kw)
        } finally {
            driver.close()
            check(dbFile.delete())
        }
    }
}
