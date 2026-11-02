package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargingSummaryTest {
    private val start = 1_800_000_000_000L
    private val snapshot = ChargingSessionSnapshot(
        60f, 20f, 80f,
        listOf(PowerSegment(start, 12f), PowerSegment(start + 3_600_000L, 24f)),
        presetName = "Home", efficiency = 0.5f, presetEdited = true
    )

    @Test
    fun summaryUsesBatteryEnergyAndTimeWeightedPowerAcrossSegments() {
        val summary = ChargingHistoryEntry(1L, snapshot, start + 7_200_000L, 50f, null, false).summary()
        assertEquals(18f, summary.energyKwh)
        assertEquals(9f, summary.averageKw)
        assertEquals(7_200_000L, summary.durationMillis)
        assertEquals(listOf(SummarySegment(12f, 3_600_000L), SummarySegment(24f, 3_600_000L)), summary.segments)
        assertEquals("Home", summary.presetName)
        assertEquals(true, summary.presetEdited)
    }

    @Test
    fun earlyStopTruncatesLastSegmentAndDoesNotIncludeFutureSegments() {
        val summary = ChargingHistoryEntry(1L, snapshot, start + 1_800_000L, 25f, null, false).summary()
        assertEquals(3f, summary.energyKwh)
        assertEquals(6f, summary.averageKw)
        assertEquals(listOf(SummarySegment(12f, 1_800_000L)), summary.segments)
    }

    @Test
    fun zeroDurationAndUnknownEndNeverDivideByZeroOrInventEnergy() {
        val summary = ChargingHistoryEntry(1L, snapshot, start, 20f, null, false).summary()
        assertEquals(0f, summary.energyKwh)
        assertNull(summary.averageKw)
        assertEquals(emptyList(), summary.segments)
        assertNull(ChargingHistoryEntry(1L, snapshot, start + 1L, null, null, false).summary().energyKwh)
    }

    @Test
    fun restoredInterruptionDoesNotTreatRestoreTimeAsChargingDuration() {
        val summary = ChargingHistoryEntry(1L, snapshot, start + 86_400_000L, null, null, false, true).summary()
        assertNull(summary.durationMillis)
        assertNull(summary.endPercent)
        assertNull(summary.energyKwh)
        assertNull(summary.averageKw)
        assertEquals(emptyList(), summary.segments)
    }

    @Test
    fun completionNotificationRequiresKnownTargetEndAndNoRestoreInterruption() {
        val entry = ChargingHistoryEntry(1L, snapshot, start + 7_200_000L, 80f, null, false)
        assertTrue(entry.reachedTarget)
        assertFalse(entry.copy(endPercent = 79.9f).reachedTarget)
        assertFalse(entry.copy(endPercent = null).reachedTarget)
        assertFalse(entry.copy(interrupted = true).reachedTarget)
    }
}
