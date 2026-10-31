package info.anodsplace.evtimer.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class StorageRepositoryTest {
    private class Fixture {
        val file = Files.createTempFile("evtimer-storage", ".preferences_pb").toFile()
        val job = Job()
        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = chargingPreferencesCorruptionHandler,
            migrations = listOf(ChargingSessionMigration()),
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }
        )
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply {
            setProperty("foreign_keys", "true")
        }, ChargingDatabase.Schema)
        val database = ChargingDatabase(driver)
        var now = 123_000L
        val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(now) }
        fun repository() = ChargingRepositoryImpl(store, database, clock, LocalStorageRestoreState)
        val repo = repository()
        suspend fun close() {
            job.cancelAndJoin()
            driver.close()
            check(file.delete())
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try { block(fixture) } finally { fixture.close() }
    }

    @Test
    fun freshInstallAndConcurrentObserversMigrateOnlyOnce() = runTest {
        fixture { f ->
            val settings = async { f.repo.observeSettings().first() }
            val session = async { f.repo.observeSession().first() }
            assertEquals(ChargingSettings.DEFAULT, settings.await())
            assertEquals(ChargingSession(), session.await())
            assertEquals(listOf("Custom"), f.repo.observePresets().first().map { it.name })
            assertEquals(1L, f.database.storageQueries.migrationDone().executeAsOne())
            assertEquals(4, f.database.storageQueries.allOptions().executeAsList().size)
        }
    }

    @Test
    fun legacySnapshotMigrationPreservesEveryFieldAndCleansOnlyStructuredKeys() = runTest {
        fixture { f ->
            val snapshot = ChargingSessionSnapshot(75f, 25f, 85f,
                listOf(PowerSegment(123L, 7f), PowerSegment(456L, 11f)), "car", "Car", 0.9f)
            val toggle = androidx.datastore.preferences.core.booleanPreferencesKey("test_toggle")
            f.store.edit {
                it[SessionPreferences.batteryCapacity] = 75f
                it[SessionPreferences.availablePowers] = "7,bad,11,NaN"
                it[SessionPreferences.startPercent] = 25f
                it[SessionPreferences.isRunning] = true
                it[SessionPreferences.startTime] = snapshot.startTime
                it[toggle] = true
                SessionPreferences.writeSnapshot(it, snapshot)
            }
            assertEquals(ChargingSession(true, snapshot), f.repo.observeSession().first())
            assertEquals(listOf(7f, 11f), f.repo.observeSettings().first().availablePowers)
            val prefs = f.store.data.first()
            assertNull(prefs[SessionPreferences.availablePowers])
            assertNull(prefs[SessionPreferences.batteryCapacity])
            assertNull(SessionPreferences.readSnapshot(prefs))
            assertEquals(25f, prefs[SessionPreferences.startPercent])
            assertEquals(true, prefs[toggle])
            assertEquals(ChargingSession(true, snapshot), f.repository().observeSession().first())
        }
    }

    @Test
    fun markerPreventsReimportAfterInterruptedPreferenceCleanup() = runTest {
        fixture { f ->
            f.repo.observeSettings().first()
            f.repo.saveSettings(ChargingSettings(60f, 7f, 20f, 80f))
            f.store.edit { it[SessionPreferences.batteryCapacity] = 999f; it[SessionPreferences.isRunning] = true }
            assertEquals(60f, f.repository().observeSettings().first().batteryCapacity)
            assertEquals(1, f.database.storageQueries.allPresets().executeAsList().size)
            assertFalse(f.repository().observeSession().first().isRunning)
            assertNull(f.store.data.first()[SessionPreferences.isRunning])
        }
    }

    @Test
    fun presetCrudSelectionAndSettingsEditsNeverChangeActiveSnapshot() = runTest {
        fixture { f ->
            val car = ChargingPreset("car", "EV", ChargingSettings(60f, 7f, 10f, 80f, listOf(7f, 11f)))
            f.repo.savePreset(car)
            f.repo.selectPreset(car.id)
            val selected = f.repo.observeSettings().first()
            assertEquals(60f, selected.batteryCapacity)
            assertEquals(ChargingSettings.DEFAULT.startPercent, selected.startPercent)
            val snapshot = ChargingSessionSnapshot.from(selected, 123L)
            f.repo.saveSession(ChargingSession(true, snapshot))
            f.repo.saveSettings(selected.copy(chargingPower = 11f, startPercent = 30f))
            assertEquals(snapshot, f.repo.observeSession().first().snapshot)
            assertEquals(11f, f.repo.observeSettings().first().chargingPower)
            f.repo.deletePreset("car")
            assertEquals(ChargingSettings.DEFAULT.copy(startPercent = 30f), f.repo.observeSettings().first())
            assertEquals(4, f.database.storageQueries.allOptions().executeAsList().size)
            assertFailsWith<IllegalArgumentException> { f.repo.deletePreset("custom") }
            assertFailsWith<IllegalArgumentException> { f.repo.selectPreset("missing") }
        }
    }

    @Test
    fun stoppingRetainsSnapshotAndHistoryCanBeReviewed() = runTest {
        fixture { f ->
            val snapshot = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L)
            f.repo.saveSession(ChargingSession(true, snapshot))
            f.repo.saveSession(ChargingSession(false, snapshot))
            assertEquals(ChargingSession(false, snapshot), f.repo.observeSession().first())
            val entry = f.repo.observeHistory().first().single()
            assertEquals(f.now, entry.endedAt)
            assertEquals(ChargingCalculator.percentAt(snapshot, f.now), entry.endPercent)
            assertEquals(snapshot, entry.snapshot)
            assertFalse(entry.reviewed)
            f.repo.reviewSession(entry.id, 62f, true)
            assertEquals(entry.copy(actualPercent = 62f, reviewed = true), f.repo.observeHistory().first().single())
            assertFailsWith<IllegalArgumentException> { f.repo.reviewSession(entry.id, Float.NaN, true) }
            assertFailsWith<IllegalArgumentException> { f.repo.reviewSession(999L, 50f, true) }
            f.repo.saveSession(ChargingSession(true, snapshot.copy(segments = listOf(PowerSegment(456L, 11f)))))
            assertEquals(456L, f.repo.observeSession().first().startTime)
            assertEquals(1, f.repo.observeHistory().first().size)
        }
    }

    @Test
    fun invalidStoredSettingsAndCorruptActiveSnapshotDoNotCrashStartup() = runTest {
        fixture { f ->
            f.repo.observeSettings().first()
            f.database.storageQueries.putPreset("custom", "Custom", 1e100, 11.0, 100.0)
            f.database.storageQueries.putSession(123L, 1e100, 20.0, 80.0, 1.0, null, null, true)
            f.database.storageQueries.putSegment(123L, 123L, 11.0)
            val restarted = f.repository()
            assertEquals(ChargingSettings.DEFAULT.batteryCapacity, restarted.observeSettings().first().batteryCapacity)
            assertFalse(restarted.observeSession().first().isRunning)
            assertTrue(f.database.storageQueries.activeSession().executeAsList().isEmpty())
        }
    }

    @Test
    fun powerSegmentUpdateRoundTripsWithoutDuplicatingSession() = runTest {
        fixture { f ->
            val snapshot = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L)
            f.repo.saveSession(ChargingSession(true, snapshot))
            val updated = snapshot.copy(segments = snapshot.segments + PowerSegment(456L, 22f), efficiency = 0.9f)
            f.repo.saveSession(ChargingSession(true, updated))
            f.repo.saveSession(ChargingSession(true, updated))
            assertEquals(updated, f.repository().observeSession().first().snapshot)
            assertEquals(2, f.database.storageQueries.allSegments().executeAsList().size)
            assertEquals(1, f.database.storageQueries.activeSession().executeAsList().size)
        }
    }

    @Test
    fun stoppedNullSessionClearsCurrentSnapshotWithoutDeletingHistory() = runTest {
        fixture { f ->
            val snapshot = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L)
            f.repo.saveSession(ChargingSession(true, snapshot))
            f.repo.saveSession(ChargingSession())
            assertEquals(ChargingSession(), f.repo.observeSession().first())
            assertEquals(ChargingSession(), f.repository().observeSession().first())
            assertEquals(snapshot, f.repo.observeHistory().first().single().snapshot)
        }
    }

    @Test
    fun legacyRunningKeysImportTheFirstSegmentAndMissingStartTimeIsRecovered() = runTest {
        fixture { f ->
            f.store.edit {
                it[SessionPreferences.batteryCapacity] = 60f
                it[SessionPreferences.chargingPower] = 7f
                it[SessionPreferences.startPercent] = 20f
                it[SessionPreferences.maxPercent] = 80f
                it[SessionPreferences.isRunning] = true
                it[SessionPreferences.startTime] = 123L
            }
            assertEquals(
                ChargingSession(true, ChargingSessionSnapshot.from(ChargingSettings(60f, 7f, 20f, 80f), 123L)),
                f.repo.observeSession().first()
            )
            assertEquals(1, f.database.storageQueries.allSegments().executeAsList().size)
        }
        fixture { f ->
            f.store.edit { it[SessionPreferences.isRunning] = true }
            assertEquals(ChargingSession(), f.repo.observeSession().first())
            assertEquals(1L, f.database.storageQueries.migrationDone().executeAsOne())
        }
    }

    @Test
    fun restoredRunningSessionBecomesInterruptedHistoryBeforeFirstLoadedStatus() = runTest {
        fixture { f ->
            val snapshot = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L)
            f.repo.saveSession(ChargingSession(true, snapshot))
            var marked = false
            val restore = object : StorageRestoreState {
                override val requiresInterruption = true
                override fun markReady() { marked = true }
            }
            f.store.edit { it[SessionPreferences.selectedPreset] = "not-in-restored-database" }
            val repository = ChargingRepositoryImpl(f.store, f.database, f.clock, restore)
            val serviceScope = CoroutineScope(Dispatchers.Default + Job())
            try {
                val service = ChargingServiceImpl(repository, serviceScope, f.clock)
                assertFalse(service.status.first { !it.isLoading }.isRunning)
                assertTrue(marked)
                assertEquals(ChargingSession(), repository.observeSession().first())
                val history = repository.observeHistory().first().single()
                assertEquals(snapshot, history.snapshot)
                assertTrue(history.interrupted)
                assertEquals(f.now, history.endedAt)
                assertNull(history.endPercent)
                assertFalse(history.reviewed)
                assertEquals("custom", f.store.data.first()[SessionPreferences.selectedPreset])
                assertEquals(ChargingSettings.DEFAULT, repository.observeSettings().first())
                assertFalse(f.repository().observeSession().first().isRunning)
                service.start()
                assertTrue(repository.observeSession().first().isRunning)
            } finally {
                serviceScope.coroutineContext[Job]!!.cancelAndJoin()
            }
        }
    }

    @Test
    fun restoredLegacyPreferencesWithoutDatabaseCannotResumeTimer() = runTest {
        fixture { f ->
            f.store.edit {
                it[SessionPreferences.isRunning] = true
                it[SessionPreferences.startTime] = 123L
            }
            val restore = object : StorageRestoreState {
                override val requiresInterruption = true
                override fun markReady() = Unit
            }
            val repository = ChargingRepositoryImpl(f.store, f.database, f.clock, restore)
            assertEquals(ChargingSession(), repository.observeSession().first())
            assertTrue(repository.observeHistory().first().single().interrupted)
        }
    }

    @Test
    fun missingPreferencesFallBackToCustomWithoutResumingRestoredSession() = runTest {
        fixture { f ->
            f.repo.savePreset(ChargingPreset("car", "EV", ChargingSettings(60f, 7f, 20f, 80f)))
            f.repo.selectPreset("car")
            f.repo.saveSession(ChargingSession(true, ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L)))
            f.store.edit { it.clear() }
            val restore = object : StorageRestoreState {
                override val requiresInterruption = true
                override fun markReady() = Unit
            }
            val repository = ChargingRepositoryImpl(f.store, f.database, f.clock, restore)
            assertEquals(ChargingSettings.DEFAULT, repository.observeSettings().first())
            assertFalse(repository.observeSession().first().isRunning)
            assertEquals(2, repository.observePresets().first().size)
        }
    }

    @Test
    fun corruptRestoredPreferencesRecoverToDefaultsWithoutLosingDatabase() = runTest {
        fixture { f ->
            // Before the first DataStore read, simulate a torn preferences file.
            f.file.writeBytes(byteArrayOf(0x0a, 0x7f))
            f.database.storageQueries.putPreset("custom", "Custom", 75.0, 7.0, 80.0)
            f.database.storageQueries.putSession(123L, 75.0, 25.0, 80.0, 1.0, null, null, true)
            f.database.storageQueries.putSegment(123L, 123L, 7.0)
            f.database.storageQueries.markMigrated()
            f.database.storageQueries.setCurrentSession(123L)
            val restore = object : StorageRestoreState {
                override val requiresInterruption = true
                override fun markReady() = Unit
            }
            val repository = ChargingRepositoryImpl(f.store, f.database, f.clock, restore)
            assertEquals(75f, repository.observeSettings().first().batteryCapacity)
            assertEquals(ChargingSettings.DEFAULT.startPercent, repository.observeSettings().first().startPercent)
            assertEquals(ChargingSession(), repository.observeSession().first())
            assertTrue(repository.observeHistory().first().single().interrupted)
        }
    }
}
