package info.anodsplace.evtimer.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChargingRepositoryTest {
    @Test
    fun nonFiniteOrInvalidSettingsUseValidDefaults() {
        val prefs = mutablePreferencesOf(
            SessionPreferences.batteryCapacity to Float.NaN,
            SessionPreferences.chargingPower to Float.POSITIVE_INFINITY,
            SessionPreferences.startPercent to -1f,
            SessionPreferences.maxPercent to 101f,
            SessionPreferences.availablePowers to "Infinity,NaN,0,-7,11"
        )
        assertEquals(ChargingSettings.DEFAULT.copy(availablePowers = listOf(11f)), SessionPreferences.readSettings(prefs))
    }

    @Test
    fun corruptSnapshotIsClearedBeforePublishingLoadedSession() = runTest {
        val prefs = mutablePreferencesOf(SessionPreferences.isRunning to true)
        SessionPreferences.writeSnapshot(prefs, ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L))
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("snapshot_segments")] = "bad"
        val migration = ChargingSessionMigration()
        assertTrue(migration.shouldMigrate(prefs))
        val recovered = migration.migrate(prefs)
        assertEquals(false, recovered[SessionPreferences.isRunning])
        assertEquals(0L, recovered[SessionPreferences.startTime])
        assertEquals(null, SessionPreferences.readSnapshot(recovered))
        assertFalse(migration.shouldMigrate(recovered))
        prefs[SessionPreferences.isRunning] = false
        assertTrue(migration.shouldMigrate(prefs))
    }

    @Test
    fun legacySessionWithMissingStartTimeIsCleared() = runTest {
        val prefs = mutablePreferencesOf(SessionPreferences.isRunning to true)
        val recovered = ChargingSessionMigration().migrate(prefs)
        assertEquals(false, recovered[SessionPreferences.isRunning])
        assertEquals(null, SessionPreferences.readSnapshot(recovered))
    }

    @Test
    fun settingsUseOneDefaultsSource() {
        assertEquals(ChargingSettings.DEFAULT, ChargingSettings())
        assertEquals(ChargingSettings.DEFAULT, SessionPreferences.readSettings(mutablePreferencesOf()))
    }

    @Test
    fun runningLegacySessionMigratesOnceUsingSavedSettingsAndStartTime() = runTest {
        val legacy = mutablePreferencesOf(
            SessionPreferences.isRunning to true,
            SessionPreferences.startTime to 1_800_000_000_123L,
            SessionPreferences.batteryCapacity to 60f,
            SessionPreferences.chargingPower to 12f,
            SessionPreferences.startPercent to 20f,
            SessionPreferences.maxPercent to 80f
        )
        val migration = ChargingSessionMigration()
        assertTrue(migration.shouldMigrate(legacy))
        val migrated = migration.migrate(legacy)
        assertEquals(
            ChargingSessionSnapshot.from(ChargingSettings(60f, 12f, 20f, 80f), 1_800_000_000_123L),
            SessionPreferences.readSnapshot(migrated)
        )
        assertEquals(true, migrated[SessionPreferences.isRunning])
        assertFalse(migration.shouldMigrate(migrated))
        assertEquals(null, SessionPreferences.readSnapshot(legacy))
    }

    @Test
    fun migrationUsesDefaultsOnlyForMissingLegacySettings() = runTest {
        val legacy = mutablePreferencesOf(
            SessionPreferences.isRunning to true,
            SessionPreferences.startTime to 123L
        )
        val migrated = ChargingSessionMigration().migrate(legacy)
        assertEquals(
            ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L),
            SessionPreferences.readSnapshot(migrated)
        )
    }

    @Test
    fun stoppedOrFreshInstallDoesNotMigrate() = runTest {
        val migration = ChargingSessionMigration()
        assertFalse(migration.shouldMigrate(mutablePreferencesOf()))
        assertFalse(migration.shouldMigrate(mutablePreferencesOf(SessionPreferences.isRunning to false)))
    }

    @Test
    fun snapshotRoundTripPreservesAllFieldsAndSegments() {
        val snapshot = ChargingSessionSnapshot(
            75f, 30f, 90f,
            listOf(PowerSegment(123L, 7.2f), PowerSegment(456_789L, 11f)),
            presetId = "car:one",
            presetName = "EV, home",
            efficiency = 0.9f
        )
        val prefs = mutablePreferencesOf()
        SessionPreferences.writeSnapshot(prefs, snapshot)
        assertEquals(snapshot, SessionPreferences.readSnapshot(prefs))
        SessionPreferences.writeSnapshot(prefs, snapshot.copy(presetId = null, presetName = null))
        assertEquals(snapshot.copy(presetId = null, presetName = null), SessionPreferences.readSnapshot(prefs))
        SessionPreferences.writeSnapshot(prefs, null)
        assertEquals(null, SessionPreferences.readSnapshot(prefs))
    }

    @Test
    fun malformedSnapshotDoesNotSilentlyBecomeAStoppedSession() {
        val prefs = mutablePreferencesOf()
        SessionPreferences.writeSnapshot(prefs, ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L))
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("snapshot_segments")] = "bad"
        assertFailsWith<IllegalArgumentException> { SessionPreferences.readSnapshot(prefs) }
        assertFailsWith<IllegalArgumentException> { ChargingSession(isRunning = true) }
    }
}
