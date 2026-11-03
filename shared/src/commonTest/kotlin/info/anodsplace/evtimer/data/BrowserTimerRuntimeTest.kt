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
}
