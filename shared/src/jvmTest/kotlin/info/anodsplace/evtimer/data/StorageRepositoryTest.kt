package info.anodsplace.evtimer.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import info.anodsplace.evtimer.db.ChargingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
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
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun settingsPowerManagementDraftSurvivesRestartAndBackupRestore() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            fixture { source ->
                source.repo.saveAsPreset("Home")
                val original = source.repo.observeConfiguration().first().selected
                val viewModel = SettingsViewModel(source.repo, source.repo, source.repo)
                try {
                    viewModel.settings.first { it != null }
                    viewModel.addPower(50f)
                    viewModel.settings.first { it?.chargingPower == 50f }
                    viewModel.removePower(11f)
                    viewModel.settings.first { it != null && 11f !in it.availablePowers }
                    viewModel.removePower(50f)
                    val expected = original.settings.copy(
                        chargingPower = 3.6f, availablePowers = listOf(3.6f, 7f, 22f)
                    )
                    viewModel.settings.first { it == expected }
                    val restarted = source.repository().observeConfiguration().first()
                    assertEquals(original, restarted.selected)
                    assertEquals(expected, restarted.settings)
                    assertTrue(restarted.isEdited)
                    val backup = BackupFormat.decode(BackupFormat.encode(source.repo.exportBackup("test")))
                    fixture { target ->
                        target.repo.restoreBackup(backup)
                        val restored = target.repository().observeConfiguration().first()
                        assertEquals(restarted, restored)
                    }
                } finally {
                    viewModel.viewModelScope.cancel()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun readingsAcknowledgeAndResetNeverOverwriteEachOtherOrActiveFactors() = runTest {
        fixture { f ->
            val home = ChargingPreset("home", "Home", ChargingSettings(60f, 6f, 20f, 90f, listOf(6f, 12f)))
            f.repo.savePreset(home)
            f.repo.selectPreset(home.id)
            f.repo.saveSettings(home.settings)
            for (start in listOf(1_000L, 4_000_000L)) {
                val snapshot = f.repo.createSnapshot(start)
                f.repo.saveSession(ChargingSession(true, snapshot))
                f.now = start + 3_600_000L
                f.repo.saveSession(ChargingSession(false, snapshot))
                f.repo.saveReading(start, 28f, true)
            }
            assertEquals(0.898f, f.repository().observeCalibration().first().factor("home", 6f), 1e-6f)
            val original = f.repo.observeHistory().first().first()
            val active = f.repo.createSnapshot(8_000_000L)
            assertEquals(0.898f, active.efficiency, 1e-6f)
            f.repo.saveSession(ChargingSession(true, active))
            f.repo.acknowledgeSession(original.id)
            assertEquals(original.copy(reviewed = true), f.repo.observeHistory().first().first())
            f.repo.saveReading(original.id, 30f, true)
            assertTrue(f.repo.observeHistory().first().first().reviewed)
            assertEquals(0.958f, f.repo.observeCalibration().first().factor("home", 6f), 1e-6f)
            assertEquals(active, f.repository().observeSession().first().snapshot)
            f.repo.renamePreset(home.id, "Renamed")
            f.repo.reorderPresets(listOf(home.id, "custom"))
            assertEquals(0.958f, f.repo.createSnapshot(9_000_000L).efficiency, 1e-6f)
            f.repo.resetLearnedEstimates("home", 6f)
            val resetHistory = f.repository().observeHistory().first()
            assertTrue(resetHistory.none { it.calibrationEnabled })
            assertEquals(listOf(30f, 28f), resetHistory.map { it.actualPercent })
            assertEquals(1f, f.repository().createSnapshot(9_000_000L).efficiency)
            assertEquals(active, f.repository().observeSession().first().snapshot)
            f.repo.saveReading(original.id, 30.5f, true)
            val faster = f.repo.createSnapshot(9_000_000L)
            assertEquals(1f, faster.efficiency, 1e-6f)
            f.repo.saveReading(original.id, 28f, true)
            assertEquals(0.94f, f.repo.createSnapshot(9_000_000L).efficiency, 1e-6f)
            f.repo.deleteSession(original.id)
            assertEquals(1f, f.repo.createSnapshot(9_000_000L).efficiency)
            assertEquals(active, f.repository().observeSession().first().snapshot)
            f.repo.changePower(ChargingSession(true, active), 8_060_000L, 12f)
            val changedPower = f.repository().observeSession().first().snapshot!!
            assertEquals(active.efficiency, changedPower.efficiency)
            assertEquals(listOf(active.efficiency, 1f), changedPower.segments.map { it.efficiency })
        }
    }

    @Test
    fun targetedResetClearsExactSpeedAcrossCapacitiesAndLeavesOtherKeys() = runTest {
        fixture { f ->
            val settings = ChargingSettings(60f, 6f, 20f, 90f, listOf(6f, 12f))
            val home = ChargingPreset("home", "Home", settings)
            f.repo.savePreset(home)
            suspend fun record(start: Long, capacity: Float, power: Float, actual: Float): Long {
                val snapshot = ChargingSessionSnapshot(
                    capacity, 20f, 90f, listOf(PowerSegment(start, power)), "home", "Home"
                )
                f.repo.saveSession(ChargingSession(true, snapshot))
                f.now = start + 3_600_000L
                f.repo.saveSession(ChargingSession(false, snapshot))
                f.repo.saveReading(start, actual, true)
                return start
            }
            val first = record(1_000L, 60f, 6f, 28f)
            val otherCapacity = record(4_000_000L, 61f, 6f, 28f)
            record(8_000_000L, 60f, 12f, 36f)
            val before = f.repo.observeHistory().first()
            assertEquals(0.94f, f.repo.observeCalibration().first().factor("home", 6f), 1e-6f)
            assertEquals(0.94f, f.repo.observeCalibration().first().factor("home", 12f), 1e-6f)

            f.repo.resetLearnedEstimates("home", 6f)
            val after = f.repo.observeHistory().first()
            assertFalse(after.first { it.id == first }.calibrationEnabled)
            assertFalse(after.first { it.id == otherCapacity }.calibrationEnabled)
            assertTrue(after.first { it.id == 8_000_000L }.calibrationEnabled)
            assertEquals(before.associate { it.id to it.actualPercent },
                after.associate { it.id to it.actualPercent })
            val calibration = f.repo.observeCalibration().first()
            assertEquals(1f, calibration.factor("home", 6f))
            assertEquals(0, calibration.estimate("home", 6f).eligibleCount)
            assertEquals(0.94f, calibration.factor("home", 12f), 1e-6f)
            f.repo.saveReading(first, 28f, true)
            assertEquals(0.94f, f.repo.observeCalibration().first().factor("home", 6f), 1e-6f)
        }
    }

    @Test
    fun customAndNamedPresetsUseExactSpeedKeysAndCapacityChangesExcludeSamples() = runTest {
        fixture { f ->
            val settings = ChargingSettings(60f, 6f, 20f, 90f, listOf(6f, 22f))
            f.repo.saveSettings(settings)
            val ac = f.repo.createSnapshot(1_000L)
            f.repo.saveSession(ChargingSession(true, ac))
            f.now = 3_601_000L
            f.repo.saveSession(ChargingSession(false, ac))
            f.repo.saveReading(ac.startTime, 28f, false)
            assertEquals(1f, f.repo.createSnapshot(4_000_000L).efficiency)
            f.repo.saveReading(ac.startTime, 28f, true)
            assertEquals(0.94f, f.repo.createSnapshot(4_000_000L).efficiency, 1e-6f)
            f.repo.saveSettings(settings.copy(chargingPower = 22f))
            assertEquals(1f, f.repo.createSnapshot(4_000_000L).efficiency)
            f.repo.savePreset(ChargingPreset("home", "Home", settings))
            f.repo.selectPreset("home")
            f.repo.saveSettings(settings)
            val home = f.repo.createSnapshot(4_000_000L)
            f.repo.saveSession(ChargingSession(true, home))
            f.now = 7_600_000L
            f.repo.saveSession(ChargingSession(false, home))
            f.repo.saveReading(home.startTime, 28f, true)
            assertEquals(0.94f, f.repo.createSnapshot(8_000_000L).efficiency, 1e-6f)
            f.repo.savePreset(ChargingPreset("home", "Home", settings.copy(batteryCapacity = 61f)))
            assertEquals(1f, f.repo.observeCalibration().first().factor("home", 6f))
            f.repo.savePreset(ChargingPreset("new-home", "New home", settings))
            f.repo.selectPreset("new-home")
            assertEquals(1f, f.repo.createSnapshot(8_000_000L).efficiency)
        }
    }

    @Test
    fun readingClearAndValidationPreserveOriginalSummaryAndConsentDefaults() = runTest {
        fixture { f ->
            val snapshot = ChargingSessionSnapshot.from(ChargingSettings(60f, 6f, 20f, 90f), 1_000L)
            f.repo.saveSession(ChargingSession(true, snapshot))
            f.now = 3_601_000L
            f.repo.saveSession(ChargingSession(false, snapshot))
            val original = f.repo.observeHistory().first().single()
            assertFalse(original.calibrationEnabled)
            f.repo.saveReading(original.id, 18.5f, true)
            val noGain = f.repo.observeHistory().first().single()
            assertEquals(18.5f, noGain.actualPercent)
            assertEquals(original.endPercent, noGain.endPercent)
            assertEquals(1f, f.repo.observeCalibration().first().factor("custom", 6f))
            f.repo.saveReading(original.id, null, true)
            assertEquals(original, f.repository().observeHistory().first().single())
            assertFailsWith<IllegalArgumentException> { f.repo.saveReading(original.id, 101f, true) }
            assertFailsWith<IllegalArgumentException> { f.repo.saveReading(original.id, Float.NaN, true) }
            assertFailsWith<IllegalArgumentException> { f.repo.saveReading(999L, 28f, true) }
            assertFailsWith<IllegalArgumentException> { f.repo.acknowledgeSession(999L) }
        }
    }

    @Test
    fun deletingNewestReviewsOnlyItsSuccessorWithoutChangingActualPercent() = runTest {
        fixture { f ->
            val settings = ChargingSettings.DEFAULT
            for (id in listOf(1_000L, 2_000L, 3_000L)) {
                val snapshot = ChargingSessionSnapshot.from(settings, id)
                f.repo.saveSession(ChargingSession(true, snapshot))
                f.now = id + 60_000L
                f.repo.saveSession(ChargingSession(false, snapshot))
            }
            f.repo.reviewSession(3_000L, null, true)
            f.repo.reviewSession(2_000L, 25f, false)
            val before = f.repo.observeHistory().first()
            assertEquals(listOf(true, false, false), before.map { it.reviewed })
            f.repo.deleteSession(3_000L)
            val after = f.repository().observeHistory().first()
            assertEquals(listOf(before[1].copy(reviewed = true), before[2]), after)
            assertEquals(25f, after.first().actualPercent)
        }
    }

    @Test
    fun deletingNonNewestDoesNotChangeReviewedFlagsOrActualPercent() = runTest {
        fixture { f ->
            for (id in listOf(1_000L, 2_000L, 3_000L)) {
                val snapshot = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, id)
                f.repo.saveSession(ChargingSession(true, snapshot))
                f.now = id + 60_000L
                f.repo.saveSession(ChargingSession(false, snapshot))
            }
            f.repo.reviewSession(3_000L, 35f, true)
            val before = f.repo.observeHistory().first()
            f.repo.deleteSession(2_000L)
            assertEquals(listOf(before[0], before[2]), f.repository().observeHistory().first())
            assertEquals(3_000L, f.repo.observeSession().first().startTime)
        }
    }

    @Test
    fun historyOrdersEndedSessionsAndAggregatesSavedSegmentsWithoutTheActiveTimer() = runTest {
        fixture { f ->
            val settings = ChargingSettings(60f, 6f, 20f, 80f)
            val start = 1_000L
            val snapshot = ChargingSessionSnapshot.from(settings, start).copy(
                presetId = "deleted", presetName = "Old home", presetEdited = true,
                segments = listOf(PowerSegment(start, 6f), PowerSegment(start + 3_600_000L, 12f))
            )
            f.repo.saveSession(ChargingSession(true, snapshot))
            f.now = start + 7_200_000L
            f.repo.saveSession(ChargingSession(false, snapshot))
            val first = f.repo.observeHistory().first().single()
            val summary = first.summary()
            assertEquals(50f, summary.endPercent)
            assertEquals(18f, summary.energyKwh)
            assertEquals(9f, summary.averageKw)
            assertEquals(7_200_000L, summary.durationMillis)
            assertEquals(listOf(SummarySegment(6f, 3_600_000L), SummarySegment(12f, 3_600_000L)), summary.segments)
            assertEquals("Old home", summary.presetName)
            assertTrue(summary.presetEdited)

            val second = ChargingSessionSnapshot.from(settings, f.now)
            f.repo.saveSession(ChargingSession(true, second))
            assertEquals(listOf(first), f.repo.observeHistory().first())
            f.now += 600_000L
            f.repo.saveSession(ChargingSession(false, second))
            // Equal finish timestamps use descending session id as the stable tie-breaker.
            val third = ChargingSessionSnapshot.from(settings, second.startTime + 1L)
            f.repo.saveSession(ChargingSession(true, third))
            f.repo.saveSession(ChargingSession(false, third))
            assertEquals(listOf(third.startTime, second.startTime, first.id),
                f.repository().observeHistory().first().map { it.id })
        }
    }

    @Test
    fun deleteCascadesSegmentsDetachesStoppedSnapshotAndSurvivesRestart() = runTest {
        fixture { f ->
            val snapshot = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 1_000L)
            f.repo.saveSession(ChargingSession(true, snapshot))
            assertFailsWith<IllegalArgumentException> { f.repo.deleteSession(snapshot.startTime) }
            assertTrue(f.repo.observeSession().first().isRunning)
            assertEquals(1, f.database.storageQueries.segments(snapshot.startTime).executeAsList().size)
            f.repo.saveSession(ChargingSession(false, snapshot))
            f.repo.deleteSession(snapshot.startTime)
            val restarted = f.repository()
            assertTrue(restarted.observeHistory().first().isEmpty())
            assertEquals(ChargingSession(), restarted.observeSession().first())
            assertTrue(f.database.storageQueries.allSegments().executeAsList().isEmpty())
            assertEquals(1, restarted.observePresets().first().size)
            assertFailsWith<IllegalArgumentException> { restarted.deleteSession(snapshot.startTime) }
        }
    }

    @Test
    fun deletingPastSessionDoesNotChangeActiveTimerAndRestoredHistoryRemainsDeletable() = runTest {
        fixture { f ->
            val past = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 1_000L)
            f.repo.saveSession(ChargingSession(true, past))
            f.repo.saveSession(ChargingSession(false, past))
            val active = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 2_000L)
            f.repo.saveSession(ChargingSession(true, active))
            f.repo.deleteSession(past.startTime)
            assertEquals(ChargingSession(true, active), f.repository().observeSession().first())
            assertTrue(f.repo.observeHistory().first().isEmpty())
            val restored = ChargingRepositoryImpl(f.store, f.database, f.clock, object : StorageRestoreState {
                override val requiresInterruption = true
                override fun markReady() = Unit
            })
            val entry = restored.observeHistory().first().single()
            assertTrue(entry.interrupted)
            assertNull(entry.summary().durationMillis)
            assertNull(entry.summary().energyKwh)
            assertNull(entry.summary().averageKw)
            restored.deleteSession(entry.id)
            assertTrue(f.repository().observeHistory().first().isEmpty())
            assertTrue(f.database.storageQueries.allSegments().executeAsList().isEmpty())
        }
    }

    @Test
    fun defaultRenameMoveEditAndDeletionPersistThroughRestartAndRestore() = runTest {
        fixture { f ->
            f.repo.savePreset(ChargingPreset("home", "Home", ChargingSettings.DEFAULT.copy(maxPercent = 80f)))
            f.repo.renamePreset("custom", "  Garage \uD83C\uDFE0  ")
            f.repo.reorderPresets(listOf("home", "custom"))
            f.repo.selectPreset("custom")
            f.repo.saveSettings(ChargingSettings.DEFAULT.withRemovedPower(11f).copy(startPercent = 25f))
            var restarted = f.repository()
            assertEquals(listOf("home", "custom"), restarted.observePresets().first().map { it.id })
            assertEquals("Garage \uD83C\uDFE0", restarted.observeConfiguration().first().selected.name)
            assertTrue(restarted.observeConfiguration().first().isEdited)
            assertEquals(3.6f, restarted.observeSettings().first().chargingPower)
            restarted.updateSelectedPreset()
            assertFalse(restarted.observeConfiguration().first().isEdited)
            assertEquals(listOf(3.6f, 7f, 22f), restarted.observeConfiguration().first().selected.settings.availablePowers)
            restarted.deletePreset("custom")
            val restore = object : StorageRestoreState {
                override val requiresInterruption = true
                override fun markReady() = Unit
            }
            restarted = ChargingRepositoryImpl(f.store, f.database, f.clock, restore)
            val configuration = restarted.observeConfiguration().first()
            assertEquals(listOf("home"), configuration.presets.map { it.id })
            assertEquals("home", configuration.selectedId)
            assertEquals(25f, configuration.settings.startPercent)
            assertFalse(configuration.isEdited)
            assertFailsWith<IllegalArgumentException> { restarted.deletePreset("home") }
            assertFailsWith<IllegalArgumentException> { restarted.renamePreset("missing", "Name") }
            restarted.saveAsPreset("Default")
            assertEquals("Default", f.repository().observeConfiguration().first().selected.name)
            assertFalse(f.repository().observePresets().first().any { it.id == "custom" })
        }
    }

    @Test
    fun renamingDoesNotDiscardWorkingDraftOrChangeChargingValues() = runTest {
        fixture { f ->
            val original = f.repo.observeConfiguration().first().selected
            f.repo.saveSettings(original.settings.withAddedPower(50f))
            f.repo.renamePreset(original.id, "New name")
            val restarted = f.repository().observeConfiguration().first()
            assertEquals(original.settings, restarted.selected.settings)
            assertEquals("New name", restarted.selected.name)
            assertTrue(restarted.isEdited)
            assertEquals(50f, restarted.settings.chargingPower)
            assertFailsWith<IllegalArgumentException> { f.repo.deletePreset(original.id) }
            assertEquals(restarted, f.repo.observeConfiguration().first())
        }
    }

    @Test
    fun presetCrudOrderingAndPowerOptionsSurviveRestart() = runTest {
        fixture { f ->
            val custom = f.repo.observePresets().first().single()
            val home = ChargingPreset("home", "\uD83C\uDFE0 Home", ChargingSettings.DEFAULT)
            val work = ChargingPreset("work", "Work", ChargingSettings.DEFAULT.copy(maxPercent = 80f))
            f.repo.savePreset(work)
            f.repo.savePreset(home)
            assertEquals(listOf("custom", "work", "home"), f.repo.observePresets().first().map { it.id })
            f.repo.reorderPresets(listOf("custom", "home", "work"))
            var restarted = f.repository()
            assertEquals(listOf("custom", "home", "work"), restarted.observePresets().first().map { it.id })
            val edited = home.copy(name = "\u26A1 Garage", settings = home.settings.withAddedPower(50f)
                .withRemovedPower(11f).copy(batteryCapacity = 60f, maxPercent = 90f))
            restarted.savePreset(edited)
            restarted = f.repository()
            assertEquals(listOf(custom, edited, work), restarted.observePresets().first())
            restarted.selectPreset(home.id)
            restarted.saveSettings(edited.settings.copy(startPercent = 25f, maxPercent = 95f))
            assertTrue(restarted.observeConfiguration().first().isEdited)
            restarted.reorderPresets(listOf("custom", "work", "home"))
            assertTrue(restarted.observeConfiguration().first().isEdited)
            assertEquals(95f, restarted.observeSettings().first().maxPercent)
            restarted.deletePreset(home.id)
            restarted = f.repository()
            val configuration = restarted.observeConfiguration().first()
            assertEquals("custom", configuration.selectedId)
            assertFalse(configuration.isEdited)
            assertEquals(custom.settings.copy(startPercent = 25f), configuration.settings)
            assertEquals(listOf("custom", "work"), configuration.presets.map { it.id })
            assertTrue(f.database.storageQueries.options(home.id).executeAsList().isEmpty())
        }
    }

    @Test
    fun invalidOrdersAndNamesAreRejectedWithoutChangingStorage() = runTest {
        fixture { f ->
            f.repo.savePreset(ChargingPreset("home", "Home", ChargingSettings.DEFAULT))
            val before = f.repo.observePresets().first()
            for (order in listOf(
                listOf("custom"), listOf("custom", "unknown"),
                listOf("custom", "home", "home"), emptyList()
            )) {
                assertFailsWith<IllegalArgumentException> { f.repo.reorderPresets(order) }
                assertEquals(before, f.repo.observePresets().first())
            }
            assertFailsWith<IllegalArgumentException> {
                f.repo.savePreset(before.last().copy(name = "dEfAuLt"))
            }
            assertFailsWith<IllegalArgumentException> {
                f.repo.savePreset(before.last().copy(name = "x".repeat(33)))
            }
            assertEquals(before, f.repository().observePresets().first())
        }
    }

    @Test
    fun editingBuiltInPowerOptionsPersistsWithoutChangingNamedPresets() = runTest {
        fixture { f ->
            f.repo.saveAsPreset("Home")
            val home = f.repo.observeConfiguration().first().selected
            f.repo.selectPreset("custom")
            val custom = f.repo.observePresets().first().first()
            val updated = custom.copy(settings = custom.settings.withAddedPower(50f).withRemovedPower(50f))
            f.repo.savePreset(updated)
            val restarted = f.repository()
            assertEquals(updated, restarted.observePresets().first().first())
            assertEquals(home, restarted.observePresets().first().last())
            assertEquals(updated.settings, restarted.observeSettings().first())
        }
    }

    @Test
    fun notifyAt80DefaultsOnAndPersistsIndependentlyOfPresets() = runTest {
        fixture { f ->
            assertTrue(f.repo.observeNotifyAt80().first())
            f.repo.setNotifyAt80(false)
            f.repo.saveAsPreset("Home")
            val restarted = f.repository()
            assertFalse(restarted.observeNotifyAt80().first())
            restarted.selectPreset(ChargingRepositoryImpl.CUSTOM_ID)
            assertFalse(restarted.observeNotifyAt80().first())
            restarted.setNotifyAt80(true)
            assertTrue(f.repository().observeNotifyAt80().first())
        }
    }

    @Test
    fun renamedDefaultSurvivesRestartWithoutChangingSnapshotsOrValues() = runTest {
        fixture { f ->
            f.repo.observeSettings().first()
            f.database.storageQueries.putPreset("custom", "Custom", 60.0, 7.0, 80.0)
            val original = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L)
                .copy(presetId = "custom", presetName = "Custom")
            f.repo.saveSession(ChargingSession(false, original))
            val restarted = f.repository()
            assertEquals("Custom", restarted.observeConfiguration().first().selected.name)
            assertEquals(60f, restarted.observeSettings().first().batteryCapacity)
            assertEquals(original, restarted.observeSession().first().snapshot)
        }
    }

    @Test
    fun namedWorkingEditsSurviveRestartAndExplicitActionsNeverOverwriteOriginal() = runTest {
        fixture { f ->
            val home = ChargingPreset("home", "\uD83C\uDFE0 Home", ChargingSettings.DEFAULT.copy(
                batteryCapacity = 60f, chargingPower = 7f, maxPercent = 80f
            ))
            f.repo.saveSettings(ChargingSettings.DEFAULT.copy(startPercent = 25f))
            f.repo.savePreset(home)
            f.repo.selectPreset(home.id)
            val applied = home.settings.copy(startPercent = 25f)
            assertEquals(applied, f.repo.observeSettings().first())
            val edited = applied.withAddedPower(50f)
            f.repo.saveSettings(edited)
            assertEquals(home, f.repo.observePresets().first().first { it.id == home.id })
            assertTrue(f.repo.observeConfiguration().first().isEdited)
            var restarted = f.repository()
            assertEquals(edited, restarted.observeSettings().first())
            assertTrue(restarted.observeConfiguration().first().isEdited)
            restarted.resetPreset()
            assertEquals(applied, restarted.observeSettings().first())
            assertFalse(restarted.observeConfiguration().first().isEdited)
            restarted.saveSettings(edited)
            restarted.updateSelectedPreset()
            assertEquals(edited.copy(startPercent = ChargingSettings.DEFAULT.startPercent),
                restarted.observePresets().first().first { it.id == home.id }.settings)
            assertFalse(restarted.observeConfiguration().first().isEdited)
            val savedHome = restarted.observePresets().first().first { it.id == home.id }
            restarted.saveSettings(edited.copy(maxPercent = 90f))
            restarted.saveAsPreset("\u26A1 Highway")
            restarted = f.repository()
            val new = restarted.observeConfiguration().first()
            assertEquals("\u26A1 Highway", new.selected.name)
            assertEquals(90f, new.settings.maxPercent)
            assertFalse(new.isEdited)
            assertEquals(savedHome, new.presets.first { it.id == home.id })
            restarted.deletePreset(new.selectedId)
            assertEquals("custom", restarted.observeConfiguration().first().selectedId)
            assertEquals(25f, restarted.observeSettings().first().startPercent)
            assertEquals("custom", restarted.observePresets().first().first().id)
        }
    }

    @Test
    fun matchingValuesClearDraftAndDefaultCanBeRenamed() = runTest {
        fixture { f ->
            f.repo.saveAsPreset("Home")
            val selected = f.repo.observeConfiguration().first()
            f.repo.saveSettings(selected.settings.withAddedPower(50f))
            f.repo.saveSettings(selected.settings.copy(startPercent = 30f))
            assertFalse(f.repo.observeConfiguration().first().isEdited)
            assertNull(f.store.data.first()[SessionPreferences.draftValues])
            assertFalse(f.repository().observeConfiguration().first().isEdited)
            f.repo.renamePreset("custom", "Renamed")
            assertFailsWith<IllegalArgumentException> { f.repo.saveAsPreset("  ") }
            assertEquals("Renamed", f.repo.observePresets().first().first().name)
        }
    }

    @Test
    fun presetRepositoryTrimsNamesAndRejectsCaseInsensitiveDuplicates() = runTest {
        fixture { f ->
            f.repo.saveAsPreset("  Home \uD83C\uDFE0  ")
            assertEquals("Home \uD83C\uDFE0", f.repo.observeConfiguration().first().selected.name)
            assertFailsWith<IllegalArgumentException> { f.repo.saveAsPreset("hOME \uD83C\uDFE0") }
            assertFailsWith<IllegalArgumentException> {
                f.repo.savePreset(ChargingPreset("another", "dEfAuLt", ChargingSettings.DEFAULT))
            }
            assertFailsWith<IllegalArgumentException> {
                f.repo.savePreset(ChargingPreset("another", "home \uD83C\uDFE0", ChargingSettings.DEFAULT))
            }
        }
    }

    @Test
    fun legacyNamesSurviveRestartAndRestoreAndAllowSettingsOnlyUpdates() = runTest {
        for (restoring in listOf(false, true)) {
            fixture { f ->
                f.repo.observeSettings().first()
                val legacyNames = listOf("a".repeat(33), "Default", "Home", "hOME", "  Legacy  ")
                val legacyPresets = legacyNames.mapIndexed { index, name ->
                    ChargingPreset("legacy-$index", name, ChargingSettings.DEFAULT)
                }
                // Seed rows as the previous release did, bypassing the new write policy.
                for (preset in legacyPresets) {
                    f.database.storageQueries.putPreset(preset.id, preset.name, 83.0, 11.0, 100.0)
                    preset.settings.availablePowers.forEachIndexed { index, power ->
                        f.database.storageQueries.putOption(preset.id, index.toLong(), power.toDouble())
                    }
                }
                val snapshot = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 123L)
                    .copy(presetId = legacyPresets.first().id, presetName = legacyNames.first())
                f.repo.saveSession(ChargingSession(restoring, snapshot))
                val restoreState = object : StorageRestoreState {
                    override val requiresInterruption = restoring
                    override fun markReady() = Unit
                }
                var restarted = ChargingRepositoryImpl(f.store, f.database, f.clock, restoreState)
                assertEquals(legacyNames, restarted.observePresets().first()
                    .filter { it.id.startsWith("legacy-") }.sortedBy { it.id }.map { it.name })
                for (preset in legacyPresets) {
                    restarted.selectPreset(preset.id)
                    val edited = restarted.observeSettings().first().withAddedPower(50f).copy(maxPercent = 80f)
                    restarted.saveSettings(edited)
                    restarted = f.repository()
                    assertEquals(preset.name, restarted.observeConfiguration().first().selected.name)
                    assertTrue(restarted.observeConfiguration().first().isEdited)
                    restarted.updateSelectedPreset()
                    restarted = f.repository()
                    val updated = restarted.observeConfiguration().first()
                    assertEquals(preset.name, updated.selected.name)
                    assertEquals(50f, updated.selected.settings.chargingPower)
                    assertEquals(80f, updated.selected.settings.maxPercent)
                    assertFalse(updated.isEdited)
                    restarted.savePreset(updated.selected.copy(settings = updated.selected.settings.copy(maxPercent = 90f)))
                    assertEquals(preset.name, restarted.observeConfiguration().first().selected.name)
                    assertEquals(90f, restarted.observeConfiguration().first().selected.settings.maxPercent)
                }
                assertEquals(legacyNames, f.repository().observePresets().first()
                    .filter { it.id.startsWith("legacy-") }.sortedBy { it.id }.map { it.name })
                if (restoring) {
                    assertEquals(snapshot, restarted.observeHistory().first().single().snapshot)
                } else {
                    assertEquals(snapshot, restarted.observeSession().first().snapshot)
                }
                restarted.savePreset(ChargingPreset("rename-target", "Valid", ChargingSettings.DEFAULT))
                val invalidNames = listOf("a".repeat(33), "dEfAuLt", "HOME")
                for (name in invalidNames) {
                    assertFailsWith<IllegalArgumentException> { restarted.saveAsPreset(name) }
                    assertFailsWith<IllegalArgumentException> {
                        restarted.savePreset(ChargingPreset("new", name, ChargingSettings.DEFAULT))
                    }
                    assertFailsWith<IllegalArgumentException> {
                        restarted.savePreset(ChargingPreset("rename-target", name, ChargingSettings.DEFAULT))
                    }
                    assertEquals("Valid", restarted.observePresets().first().first { it.id == "rename-target" }.name)
                }
                val legacy = restarted.observePresets().first().first { it.id == legacyPresets.first().id }
                assertFailsWith<IllegalArgumentException> { restarted.savePreset(legacy.copy(name = "Default")) }
                restarted.savePreset(legacy.copy(name = "  Renamed  "))
                assertEquals("Renamed", f.repository().observePresets().first().first { it.id == legacy.id }.name)
                if (restoring) {
                    assertEquals(snapshot, f.repository().observeHistory().first().single().snapshot)
                } else {
                    assertEquals(snapshot, f.repository().observeSession().first().snapshot)
                }
            }
        }
    }

    @Test
    fun editedSessionMetadataSurvivesRestartDeletionHistoryAndRestore() = runTest {
        fixture { f ->
            f.repo.saveAsPreset("Home")
            val selected = f.repo.observeConfiguration().first()
            f.repo.saveSettings(selected.settings.withAddedPower(50f))
            val scope = CoroutineScope(Dispatchers.Default + Job())
            val snapshot: ChargingSessionSnapshot
            try {
                val service = ChargingServiceImpl(f.repo, scope, f.clock)
                service.start()
                snapshot = requireNotNull(f.repo.observeSession().first().snapshot)
                assertEquals(selected.selectedId, snapshot.presetId)
                assertEquals("Home", snapshot.presetName)
                assertTrue(snapshot.presetEdited)
                assertEquals(50f, snapshot.segments.single().powerKw)
            } finally {
                scope.coroutineContext[Job]!!.cancelAndJoin()
            }
            assertEquals(snapshot, f.repository().observeSession().first().snapshot)
            f.repo.deletePreset(selected.selectedId)
            val restore = object : StorageRestoreState {
                override val requiresInterruption = true
                override fun markReady() = Unit
            }
            val restored = ChargingRepositoryImpl(f.store, f.database, f.clock, restore)
            assertFalse(restored.observeSession().first().isRunning)
            assertEquals(snapshot, restored.observeHistory().first().single().snapshot)
            assertTrue(restored.observeHistory().first().single().interrupted)
        }
    }

    @Test
    fun staleMissingAndMalformedRestoredDraftsFallBackToCustomWithoutLosingPresets() = runTest {
        listOf("missing", "stale", "malformed", "partial").forEach { scenario ->
            fixture { f ->
                f.repo.saveAsPreset("Home")
                val selected = f.repo.observeConfiguration().first().selectedId
                f.store.edit {
                    it[SessionPreferences.draftPreset] = if (scenario == "stale") "other" else selected
                    if (scenario != "partial") it[SessionPreferences.draftValues] =
                        if (scenario == "malformed") "60,NaN,80,7" else "60,7,80,7"
                    if (scenario == "missing") it[SessionPreferences.selectedPreset] = "gone"
                }
                val restored = f.repository().observeConfiguration().first()
                assertEquals("custom", restored.selectedId)
                assertFalse(restored.isEdited)
                assertEquals(2, restored.presets.size)
                assertNull(f.store.data.first()[SessionPreferences.draftValues])
                assertNull(f.store.data.first()[SessionPreferences.draftPreset])
            }
        }
    }

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
            assertEquals(listOf("Default"), f.repo.observePresets().first().map { it.name })
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
    fun earlyStopSummaryAndReviewPersistAcrossRepositoryRestart() = runTest {
        fixture { f ->
            val snapshot = ChargingSessionSnapshot.from(ChargingSettings(60f, 12f, 20f, 80f), f.now - 3_600_000L)
            f.repo.saveSession(ChargingSession(true, snapshot))
            f.repo.saveSession(ChargingSession(false, snapshot))
            val entry = f.repository().observeHistory().first().single()
            assertFalse(entry.reviewed)
            assertEquals(f.now, entry.endedAt)
            assertEquals(40f, entry.endPercent)
            assertEquals(12f, entry.summary().energyKwh)
            assertEquals(12f, entry.summary().averageKw)
            f.repo.reviewSession(entry.id, null, true)
            assertTrue(f.repository().observeHistory().first().single().reviewed)
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
    fun lateCompletionRecordsTargetTimeRatherThanTimeAppWoke() = runTest {
        fixture { f ->
            val snapshot = ChargingSessionSnapshot.from(
                ChargingSettings(60f, 12f, 20f, 80f), f.now - 181 * 60_000L
            )
            f.repo.saveSession(ChargingSession(true, snapshot))
            f.repo.saveSession(ChargingSession(false, snapshot))
            val entry = f.repository().observeHistory().first().single()
            assertEquals(f.now - 60_000L, entry.endedAt)
            assertEquals(80f, entry.endPercent)
            assertFalse(entry.reviewed)
            assertEquals(180 * 60_000L, entry.summary().durationMillis)
            assertEquals(36f, entry.summary().energyKwh)
            assertEquals(12f, entry.summary().averageKw)
        }
    }

    @Test
    fun invalidStoredSettingsAndCorruptActiveSnapshotDoNotCrashStartup() = runTest {
        fixture { f ->
            f.repo.observeSettings().first()
            f.database.storageQueries.putPreset("custom", "Default", 1e100, 11.0, 100.0)
            f.database.storageQueries.putSession(123L, 1e100, 20.0, 80.0, 1.0, null, null, true, false)
            f.database.storageQueries.putSegment(123L, 123L, 11.0, null)
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
            f.database.storageQueries.putPreset("custom", "Default", 75.0, 7.0, 80.0)
            f.database.storageQueries.putSession(123L, 75.0, 25.0, 80.0, 1.0, null, null, true, false)
            f.database.storageQueries.putSegment(123L, 123L, 7.0, null)
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

    @Test
    fun speedChangeSurvivesRestartAndKeepsPresetValuesUntouched() = runTest {
        fixture { f ->
            val settings = ChargingSettings(60f, 6f, 20f, 90f, listOf(6f, 12f))
            val preset = ChargingPreset("home", "Home", settings)
            f.repo.savePreset(preset)
            f.repo.selectPreset(preset.id)
            val completed = ChargingSessionSnapshot.from(settings, 1_000L)
                .copy(presetId = preset.id, presetName = preset.name)
            f.repo.saveSession(ChargingSession(true, completed))
            f.now = 3_601_000L
            f.repo.saveSession(ChargingSession(false, completed))
            f.repo.saveReading(completed.startTime, 28f, true)
            val before = f.repo.observeConfiguration().first()
            val scope = CoroutineScope(Dispatchers.Default + Job())
            try {
                val service = ChargingServiceImpl(f.repo, scope, f.clock)
                service.start()
                service.changePower(12f)
            } finally {
                scope.coroutineContext[Job]!!.cancelAndJoin()
            }
            val restarted = f.repository()
            val session = restarted.observeSession().first()
            assertTrue(session.isRunning)
            assertEquals(2, session.snapshot!!.segments.size)
            assertEquals(12f, session.snapshot!!.segments.last().powerKw)
            assertEquals(listOf(0.94f, 1f), session.snapshot!!.segments.map { it.efficiency })
            val after = restarted.observeConfiguration().first()
            assertEquals(before.settings, after.settings)
            assertEquals(before.isEdited, after.isEdited)
        }
    }
}