package info.anodsplace.evtimer.data

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

internal class BrowserTestStore : BrowserDocumentStore {
    var record: StoredBrowserDocument? = null
    var failWrite = false
    override suspend fun read() = record
    override suspend fun compareAndSet(expectedRevision: String?, payload: String): StoredBrowserDocument {
        check(!failWrite) { "Quota exceeded" }
        check(record?.revision == expectedRevision) { "Another tab changed the document" }
        return StoredBrowserDocument(((record?.revision?.toLong() ?: 0L) + 1).toString(), payload)
            .also { record = it }
    }
}

internal class BrowserTestClock(var epoch: Long = 1_700_000_000_000) : Clock {
    override fun now() = Instant.fromEpochMilliseconds(epoch)
}

class BrowserTimerRuntimeTest {
    private val clock = BrowserTestClock()
    private val store = BrowserTestStore()
    private fun runtime() = BrowserTimerRuntime(BrowserStorage(store, clock), clock)

    private suspend fun configured(): BrowserTimerRuntime = runtime().also {
        it.reconcile()
        it.configure(ConfigurationViewEvent.UpdateBatteryCapacity(100f))
        it.configure(ConfigurationViewEvent.UpdateChargingPower(10f))
        it.configure(ConfigurationViewEvent.UpdateStartPercent(20f))
        it.configure(ConfigurationViewEvent.UpdateMaxPercent(90f))
    }

    @Test
    fun reloadAndResumeIntegrateSavedEpochInsteadOfTickCount() = runTest {
        val runtime = configured()
        val started = runtime.start()
        clock.epoch += 2 * 3_600_000L
        val resumed = runtime().reconcile()
        val view = assertNotNull(resumed.runningViewState(clock.epoch, ClockTimeFormatter { "$it" }))
        assertEquals(40f, view.calculation.estimatedPercent)
        assertEquals(120, view.elapsedMinutes)
        assertEquals(300, view.calculation.timeRemainingMinutes)
        assertEquals(started.activeSession, resumed.activeSession)
        val record = store.record
        repeat(10) { runtime().reconcile() }
        assertEquals(record, store.record, "Refresh must not rewrite a still-running snapshot")
    }

    @Test
    fun stoppedSummaryAndHistoryRemainSavedUntilExplicitAcknowledgmentOrDeletion() = runTest {
        val runtime = configured()
        runtime.start()
        clock.epoch += 3_600_000L
        val stopped = runtime.stop()
        val entry = stopped.historyEntries().single()
        assertFalse(entry.reviewed)
        assertEquals(30f, entry.endPercent)
        assertEquals(10f, entry.summary().energyKwh)
        assertEquals(3_600_000L, entry.summary().durationMillis)
        assertEquals(entry, runtime().reconcile().historyEntries().single())
        val acknowledged = runtime.acknowledgeCompletion(entry.id).historyEntries().single()
        assertEquals(entry.copy(reviewed = true), acknowledged)
        store.failWrite = true
        assertFailsWith<IllegalStateException> { runtime.deleteHistory(entry.id) }
        assertEquals(acknowledged, runtime().reconcile().historyEntries().single())
        store.failWrite = false
        assertTrue(runtime.deleteHistory(entry.id).historyEntries().isEmpty())
        assertTrue(runtime().reconcile().historyEntries().isEmpty())
    }

    @Test
    fun repeatedStartCannotResetAnExistingTimer() = runTest {
        val runtime = configured()
        val started = runtime.start()
        clock.epoch += 60_000
        assertEquals(started, runtime().start())
    }

    @Test
    fun completionIsDurableExactlyOnceAtTargetEpochNotResumeEpoch() = runTest {
        val runtime = configured()
        val active = assertNotNull(runtime.start().activeSession)
        val expectedEnd = active.snapshot().targetTime()
        clock.epoch += 24 * 3_600_000L
        val completed = runtime().reconcile()
        assertNull(completed.activeSession)
        val history = completed.backup.sessions.single()
        assertEquals(expectedEnd, history.endedAt)
        assertEquals(90.0, history.endPercent)
        repeat(3) { assertEquals(completed, runtime().reconcile()) }
        assertEquals(1, runtime.storage.exportBackup("test").sessions.size)
    }

    @Test
    fun powerSegmentsAndFrozenCalibrationSurviveReload() = runTest {
        val runtime = configured()
        runtime.start()
        clock.epoch += 3_600_000L
        runtime.changePower(20f)
        clock.epoch += 3_600_000L
        val active = assertNotNull(runtime().reconcile().activeSession)
        assertEquals(2, active.segments.size)
        assertEquals(50f, ChargingCalculator.percentAt(active.snapshot(), clock.epoch))
        assertEquals(1.0, active.segments.last().efficiency)
    }

    @Test
    fun importedConsentedReadingsUseExistingCalibrationAndFreezeEverySegment() = runTest {
        val runtime = configured()
        runtime.storage.update { state ->
            state.copy(backup = state.backup.copy(sessions = listOf(BackupSession(
                id = 1, batteryCapacity = 100.0, startPercent = 20.0, targetPercent = 90.0,
                efficiency = 1.0, presetId = CUSTOM_PRESET_ID, presetName = "Default",
                segments = listOf(BackupSegment(clock.epoch - 7_200_000, 10.0, 1.0)),
                endedAt = clock.epoch - 3_600_000, endPercent = 30.0, actualPercent = 28.0,
                reviewed = true, calibrationEnabled = true
            ))))
        }
        val active = assertNotNull(runtime.start().activeSession)
        assertEquals(0.94f, active.efficiency.toFloat(), 0.00001f)
        assertEquals(active.efficiency, active.segments.single().efficiency)
        clock.epoch += 3_600_000
        val changed = assertNotNull(runtime.changePower(20f).activeSession)
        assertEquals(1.0, changed.segments.last().efficiency)
        assertEquals(active.segments.single(), runtime().reconcile().activeSession?.segments?.first())
    }

    @Test
    fun backwardsClockCannotDecreaseBelowStartOrMakeDuplicateIds() = runTest {
        val runtime = configured()
        val first = assertNotNull(runtime.start().activeSession)
        clock.epoch -= 60_000
        val active = assertNotNull(runtime.reconcile().activeSession)
        assertEquals(20f, ChargingCalculator.percentAt(active.snapshot(), clock.epoch))
        assertEquals(first.segments.first().startedAt, runtime.stop().backup.sessions.single().endedAt)
        assertTrue(assertNotNull(runtime.start().activeSession).id > first.id)
    }

    @Test
    fun writeFailureDoesNotPretendToStartOrComplete() = runTest {
        val runtime = configured()
        store.failWrite = true
        assertFailsWith<IllegalStateException> { runtime.start() }
        assertNull(runtime.storage.load().activeSession)
        store.failWrite = false
        val started = runtime.start()
        clock.epoch += 24 * 3_600_000
        store.failWrite = true
        assertFailsWith<IllegalStateException> { runtime.reconcile() }
        assertEquals(started.activeSession, runtime.storage.load().activeSession)
        store.failWrite = false
        assertNull(runtime.reconcile().activeSession)
    }

    @Test
    fun presetsDraftsAndActiveSessionBackupGuardRemainWired() = runTest {
        val runtime = configured()
        runtime.configure(ConfigurationViewEvent.SaveAsPreset("My car"))
        val started = runtime.start()
        assertEquals("My car", started.activeSession?.presetName)
        val backup = runtime.storage.exportBackup("test")
        assertTrue(backup.sessions.isEmpty())
        assertFailsWith<BackupException> { runtime.storage.restoreBackup(backup) }
        runtime.stop()
        runtime.storage.restoreBackup(backup)
        assertNull(runtime.reconcile().activeSession)
        assertEquals("My car", runtime.reconcile().configuration().selected.name)
    }

    @Test
    fun presetManagementUsesDurableOrderNamesAndSelectedFallback() = runTest {
        val runtime = configured()
        val first = runtime.reconcile().configuration().selectedId
        val second = runtime.configure(ConfigurationViewEvent.SaveAsPreset("Second")).configuration().selectedId
        val third = runtime.configure(ConfigurationViewEvent.SaveAsPreset("Third")).configuration().selectedId
        runtime.renamePreset(second, "  Home  ")
        assertEquals("Home", runtime().reconcile().configuration().presets.first { it.id == second }.name)
        runtime.reorderPresets(listOf(third, second, first))
        assertEquals(listOf(third, second, first), runtime().reconcile().configuration().presets.map { it.id })
        runtime.movePreset(first, -1)
        assertEquals(listOf(third, first, second), runtime().reconcile().configuration().presets.map { it.id })
        assertFailsWith<IllegalArgumentException> { runtime.reorderPresets(listOf(first, first, second)) }
        assertFailsWith<IllegalArgumentException> { runtime.renamePreset(first, "HOME") }
        val deleted = runtime.deletePreset(third).configuration()
        assertEquals(first, deleted.selectedId)
        runtime.deletePreset(second)
        assertFailsWith<IllegalArgumentException> { runtime.deletePreset(first) }
    }

    @Test
    fun settingsEditsCannotMutateActiveSnapshotAndRemovedSelectionFallsBack() = runTest {
        val runtime = configured()
        val active = runtime.start().activeSession
        runtime.addPower(8.5f)
        assertEquals(8.5f, runtime.reconcile().configuration().settings.chargingPower)
        runtime.removePower(8.5f)
        assertEquals(3.6f, runtime.reconcile().configuration().settings.chargingPower)
        runtime.removePower(7f)
        assertFalse(7f in runtime().reconcile().configuration().settings.availablePowers)
        runtime.configure(ConfigurationViewEvent.UpdateBatteryCapacity(75f))
        runtime.setNotifyAt80(false)
        val reloaded = runtime().reconcile()
        assertEquals(active, reloaded.activeSession)
        assertFalse(reloaded.backup.preferences.notifyAt80)
        assertEquals(75f, reloaded.configuration().settings.batteryCapacity)
    }

    @Test
    fun undoPowerRemovalSurvivesReloadForSelectedAndUnselectedPowers() = runTest {
        val runtime = configured()
        runtime.addPower(10f)
        val initial = runtime.reconcile().configuration().settings
        val selected = runtime.removePowerForUndo(initial.chargingPower)
        assertTrue(selected)
        runtime.restorePower(initial.chargingPower, selected)
        assertEquals(initial, runtime().reconcile().configuration().settings)

        val unselected = runtime.removePowerForUndo(7f)
        assertFalse(unselected)
        runtime.restorePower(7f, unselected)
        assertEquals(initial, runtime().reconcile().configuration().settings)
    }

    @Test
    fun readingsLearningResetAndBackupRemainPortableAndNeverRewriteFrozenSegments() = runTest {
        val runtime = configured()
        runtime.configure(ConfigurationViewEvent.UpdatePreset)
        runtime.start()
        clock.epoch += 3_600_000
        val entry = runtime.stop().historyEntries().single()
        runtime.saveReading(entry.id, 28f, true)
        val learned = runtime().reconcile()
        assertEquals(28f, learned.historyEntries().single().actualPercent)
        assertEquals(1, learned.calibration().estimate(entry.snapshot.presetId, 10f).eligibleCount)
        val active = runtime.start().activeSession
        assertEquals(0.94f, active!!.efficiency.toFloat(), 0.00001f)
        runtime.resetLearnedEstimates(entry.snapshot.presetId!!, 10f)
        val reset = runtime().reconcile()
        assertEquals(active, reset.activeSession)
        assertEquals(0, reset.calibration().estimate(entry.snapshot.presetId, 10f).eligibleCount)
        assertEquals(28f, reset.historyEntries().single().actualPercent)
        runtime.saveReading(entry.id, null, true)
        assertFalse(runtime.reconcile().historyEntries().single().calibrationEnabled)
        assertFailsWith<IllegalArgumentException> { runtime.saveReading(entry.id, Float.NaN, true) }
        assertFailsWith<IllegalArgumentException> { runtime.saveReading(-1, 50f, true) }
        runtime.stop()
        val backup = BackupFormat.decode(BackupFormat.encode(runtime.storage.exportBackup("test")))
        runtime.storage.restoreBackup(backup)
        assertEquals(backup.sessions.map { it.actualPercent }, runtime.reconcile().backup.sessions.map { it.actualPercent })
    }

    @Test
    fun changingConfigurationDuringChargeCannotChangeItsCalibrationCapacity() = runTest {
        val runtime = configured()
        runtime.configure(ConfigurationViewEvent.UpdatePreset)
        runtime.start()
        clock.epoch += 3_600_000
        val entry = runtime.stop().historyEntries().single()
        runtime.saveReading(entry.id, 28f, true)
        runtime.start()
        runtime.configure(ConfigurationViewEvent.UpdateBatteryCapacity(75f))
        clock.epoch += 60_000
        runtime.changePower(20f)
        clock.epoch += 60_000
        val active = runtime.changePower(10f).activeSession!!
        assertEquals(100.0, active.batteryCapacity)
        assertEquals(0.94f, active.segments.last().efficiency!!.toFloat(), 0.00001f)
    }

    @Test
    fun settingsWriteFailuresLeavePersistedDataUnchanged() = runTest {
        val runtime = configured()
        val original = runtime.reconcile()
        store.failWrite = true
        assertFailsWith<IllegalStateException> { runtime.renamePreset(original.configuration().selectedId, "Changed") }
        assertFailsWith<IllegalStateException> { runtime.addPower(8.5f) }
        assertFailsWith<IllegalStateException> { runtime.setNotifyAt80(false) }
        assertEquals(original, runtime.storage.load())
    }

    @Test
    fun lastPowerCannotBeRemovedEvenIfSelectionIsNotInTheOptions() = runTest {
        val runtime = configured()
        for (power in listOf(3.6f, 7f, 11f)) runtime.removePower(power)
        assertEquals(listOf(22f), runtime.reconcile().configuration().settings.availablePowers)
        assertFailsWith<IllegalArgumentException> { runtime.removePower(22f) }
        assertFailsWith<IllegalArgumentException> { runtime.removePower(99f) }
    }
}
