package info.anodsplace.evtimer.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargingPersistenceTest {
    @Test
    fun legacyUpgradeAndProcessRestartPreserveSessionAndEditedSettings() = runTest {
        val file = Files.createTempFile("evtimer-session", ".preferences_pb").toFile()
        val oldJob = Job()
        val newJob = Job()
        val restartJob = Job()
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
            val repo = ChargingRepositoryImpl(upgraded)
            val session = repo.observeSession().first()
            assertTrue(session.isRunning)
            assertEquals(1_800_000_000_123L, session.startTime)
            val expected = ChargingSessionSnapshot.from(ChargingSettings(60f, 12f, 20f, 80f), session.startTime)
            assertEquals(expected, session.snapshot)
            val newSettings = ChargingSettings(100f, 50f, 10f, 90f)
            repo.saveSettings(newSettings)
            newJob.cancelAndJoin()
            val restarted = PreferenceDataStoreFactory.create(
                migrations = listOf(ChargingSessionMigration()),
                scope = CoroutineScope(Dispatchers.IO + restartJob), produceFile = { file }
            )
            val restored = ChargingRepositoryImpl(restarted)
            assertEquals(session, restored.observeSession().first())
            assertEquals(newSettings, restored.observeSettings().first())
            val segmented = expected.copy(
                presetId = "ev", presetName = "My EV", efficiency = 0.9f,
                segments = expected.segments + PowerSegment(expected.startTime + 60_000L, 24f)
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
            check(file.delete())
        }
    }
}
