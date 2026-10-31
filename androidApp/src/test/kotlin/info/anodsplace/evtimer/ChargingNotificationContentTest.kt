package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ChargingCalculation
import info.anodsplace.evtimer.data.ChargingSessionSnapshot
import info.anodsplace.evtimer.data.ChargingStatus
import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.PowerSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargingNotificationContentTest {
    private val formatter = ClockTimeFormatter { "14:30" }
    private val snapshot = ChargingSessionSnapshot(
        batteryCapacity = 100f, startPercent = 40f, targetPercent = 80f,
        segments = listOf(PowerSegment(1_000L, 10f), PowerSegment(3_601_000L, 20f))
    )
    private val status = ChargingStatus(
        isLoading = false, isRunning = true, startTime = snapshot.startTime,
        currentPercent = 45.9f, targetPercent = 80f, snapshot = snapshot,
        estimatedEndTime = 10_000L,
        calculation = ChargingCalculation(timeRemainingMinutes = 75, chargingSpeed = 10f)
    )

    @Test
    fun fallbackTextRetainsProgressSpeedRemainingAndPlatformEta() {
        val content = ChargingNotificationContent.from(status, formatter)
        assertEquals("45% → 80% • 10.0 kW • 1h 15m left • ETA 14:30", content.text)
        assertEquals(45, content.percent)
        assertEquals("45%", content.criticalText)
        assertEquals(snapshot.startTime, content.sessionId)
    }

    @Test
    fun completionTextUsesTargetAndRemovesRemainingDuration() {
        val content = ChargingNotificationContent.from(
            status.copy(currentPercent = 80f, calculation = status.calculation.copy(timeRemainingMinutes = 0)),
            formatter
        )
        assertEquals("Target reached · 80% • 10.0 kW • ETA 14:30", content.text)
        assertEquals("80%", content.criticalText)
    }

    @Test
    fun unknownEtaIsNotFormattedOrShown() {
        val content = ChargingNotificationContent.from(
            status.copy(estimatedEndTime = 0, calculation = status.calculation.copy(timeRemainingMinutes = 5)),
            ClockTimeFormatter { error("No ETA should be formatted") }
        )
        assertEquals("45% → 80% • 10.0 kW • 5m left", content.text)
    }

    @Test
    fun speedChangesMapToAbsoluteBatteryPercentAndSegmentsSumTo100() {
        val content = ChargingNotificationContent.from(status, formatter)
        assertEquals(listOf(50), content.speedChangePoints)
        assertEquals(listOf(50, 50), content.segmentLengths)
        assertEquals(100, content.segmentLengths.sum())
    }

    @Test
    fun singleSpeedHasOneFullScaleSegment() {
        val content = ChargingNotificationContent.from(
            status.copy(snapshot = snapshot.copy(segments = snapshot.segments.take(1))), formatter
        )
        assertEquals(listOf(100), content.segmentLengths)
        assertTrue(content.speedChangePoints.isEmpty())
    }

    @Test
    fun fractionalAndTargetCappedChangesNeverProduceZeroLengthSegments() {
        val content = ChargingNotificationContent.from(
            status.copy(snapshot = snapshot.copy(segments = listOf(
                PowerSegment(1_000L, 10f), PowerSegment(2_000L, 11f),
                PowerSegment(3_000L, 12f), PowerSegment(100_000_000L, 20f)
            ))), formatter
        )
        assertEquals(listOf(40, 80), content.speedChangePoints)
        assertEquals(listOf(40, 40, 20), content.segmentLengths)
        assertTrue(content.segmentLengths.all { it > 0 })
    }

    @Test
    fun percentIsClampedForChipAndFallbackProgressBar() {
        assertEquals("100%", ChargingNotificationContent.from(status.copy(currentPercent = 105f), formatter).criticalText)
        assertEquals("0%", ChargingNotificationContent.from(status.copy(currentPercent = -1f), formatter).criticalText)
    }

    @Test
    fun zeroPercentSpeedChangesDoNotCreateInvalidFrameworkPoints() {
        val content = ChargingNotificationContent.from(
            status.copy(snapshot = snapshot.copy(
                startPercent = 0f, segments = listOf(PowerSegment(1_000L, 10f), PowerSegment(2_000L, 11f))
            )), formatter
        )
        assertTrue(content.speedChangePoints.isEmpty())
        assertEquals(listOf(100), content.segmentLengths)
    }

    @Test
    fun subPercentTicksDoNotChangeContent() {
        val original = ChargingNotificationContent.from(status, formatter)
        assertEquals(original, ChargingNotificationContent.from(status.copy(currentPercent = 45.99f, elapsedMinutes = 1), formatter))
    }

    @Test
    fun remainingMinuteSpeedAndCompletionChangesUpdateEvenAtSamePercent() {
        val original = ChargingNotificationContent.from(status, formatter)
        listOf(
            status.copy(calculation = status.calculation.copy(timeRemainingMinutes = 74)),
            status.copy(calculation = status.calculation.copy(chargingSpeed = 11f)),
            status.copy(calculation = status.calculation.copy(timeRemainingMinutes = 0)),
            status.copy(currentPercent = 46f)
        ).forEach { assertFalse(original == ChargingNotificationContent.from(it, formatter)) }
    }
}
