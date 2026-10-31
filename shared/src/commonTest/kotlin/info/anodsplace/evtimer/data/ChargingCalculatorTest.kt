package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChargingCalculatorTest {
    private val snapshot = ChargingSessionSnapshot(
        60f, 20f, 80f, listOf(PowerSegment(1_800_000_000_000L, 12f))
    )

    @Test
    fun progressUsesMillisecondsRatherThanWholeMinutes() {
        val start = snapshot.startTime
        assertEquals(20f, ChargingCalculator.percentAt(snapshot, start - 1L))
        assertEquals(20.166666f, ChargingCalculator.percentAt(snapshot, start + 30_000L), 0.00001f)
        assertEquals(20.333334f, ChargingCalculator.percentAt(snapshot, start + 60_001L), 0.00001f)
        assertEquals(179.9999833333, ChargingCalculator.minutesToReach(snapshot, start + 1L, 80f), 0.0000001)
        assertEquals(30f, ChargingCalculator.predict(snapshot, start, 30.0))
    }

    @Test
    fun timeToReachSupportsEightyPercentBeforeAtAndBeyondTarget() {
        val start = snapshot.startTime
        assertEquals(180.0, ChargingCalculator.minutesToReach(snapshot, start, 80f))
        assertEquals(0.0, ChargingCalculator.minutesToReach(snapshot, start + 180 * 60_000L, 80f))
        assertEquals(0.0, ChargingCalculator.minutesToReach(snapshot, start + 181 * 60_000L, 80f))
        val lowerTarget = snapshot.copy(targetPercent = 60f)
        assertEquals(180.0, ChargingCalculator.minutesToReach(lowerTarget, start, 80f))
        val aboveEighty = snapshot.copy(startPercent = 85f, targetPercent = 100f)
        assertEquals(0.0, ChargingCalculator.minutesToReach(aboveEighty, start, 80f))
        assertEquals(240.0, ChargingCalculator.minutesToReach(snapshot, start, 100f))
        assertEquals(80f, ChargingCalculator.predict(snapshot, start, 300.0))
    }

    @Test
    fun milestoneEstimateIncludesRoundedMinutesAndEta() {
        val start = snapshot.startTime
        val targetAboveEighty = snapshot.copy(targetPercent = 100f, segments = listOf(PowerSegment(start, 11f)))

        assertEquals(
            MilestoneEstimate(minutesRemaining = 196, estimatedTime = start + 11_781_819L),
            ChargingCalculator.estimateMilestone(targetAboveEighty, start)
        )
    }

    @Test
    fun milestoneEstimateIsAbsentWhenTargetOrStartPercentReachesEighty() {
        val start = snapshot.startTime
        assertEquals(null, ChargingCalculator.estimateMilestone(snapshot, start))
        assertEquals(
            null,
            ChargingCalculator.estimateMilestone(
                snapshot.copy(startPercent = 80f, targetPercent = 100f),
                start
            )
        )
        assertEquals(null, ChargingCalculator.estimateMilestone(snapshot.copy(targetPercent = 60f), start))
        assertEquals(null, ChargingCalculator.estimateMilestone(snapshot.copy(startPercent = 85f, targetPercent = 100f), start))
    }

    @Test
    fun milestoneCrossingTimeStaysStableAcrossLaterTicksAndRestoredSnapshots() {
        val session = snapshot.copy(targetPercent = 100f)
        val crossing = session.startTime + 180 * 60_000L
        val reached = MilestoneEstimate(0, crossing, reached = true)
        assertEquals(MilestoneEstimate(0, crossing), ChargingCalculator.estimateMilestone(session, crossing - 1))
        assertEquals(reached, ChargingCalculator.estimateMilestone(session, crossing))
        assertEquals(reached, ChargingCalculator.estimateMilestone(session, crossing + 60_000L))
        assertEquals(reached, ChargingCalculator.estimateMilestone(session.copy(), crossing + 180 * 60_000L))
    }

    @Test
    fun milestoneCrossingTimeIntegratesSpeedChangeBeforeEightyAndIgnoresChangesAfterIt() {
        val start = snapshot.startTime
        val session = snapshot.copy(
            targetPercent = 100f,
            segments = listOf(PowerSegment(start, 12f), PowerSegment(start + 60 * 60_000L, 24f))
        )
        val crossing = start + 120 * 60_000L
        assertEquals(MilestoneEstimate(30, crossing), ChargingCalculator.estimateMilestone(session, start + 90 * 60_000L))
        val reached = MilestoneEstimate(0, crossing, reached = true)
        assertEquals(reached, ChargingCalculator.estimateMilestone(session, crossing))
        val later = session.copy(segments = session.segments + PowerSegment(crossing + 10 * 60_000L, 7f))
        assertEquals(reached, ChargingCalculator.estimateMilestone(later, crossing + 60 * 60_000L))
    }

    @Test
    fun segmentsIntegrateEnergyWithoutRewritingThePast() {
        val start = snapshot.startTime
        val segmented = snapshot.copy(
            targetPercent = 100f,
            segments = listOf(PowerSegment(start, 12f), PowerSegment(start + 60 * 60_000L, 24f))
        )
        assertEquals(30f, ChargingCalculator.percentAt(segmented, start + 30 * 60_000L))
        assertEquals(40f, ChargingCalculator.percentAt(segmented, start + 60 * 60_000L))
        assertEquals(80f, ChargingCalculator.percentAt(segmented, start + 120 * 60_000L))
        assertEquals(120.0, ChargingCalculator.minutesToReach(segmented, start, 80f))
        assertEquals(30.0, ChargingCalculator.minutesToReach(segmented, start + 90 * 60_000L, 80f))
        assertEquals(80f, ChargingCalculator.predict(segmented, start + 30 * 60_000L, 90.0))
    }

    @Test
    fun invalidInputIsRejectedExplicitly() {
        assertFailsWith<IllegalArgumentException> { PowerSegment(0L, 0f) }
        assertFailsWith<IllegalArgumentException> { snapshot.copy(batteryCapacity = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { snapshot.copy(efficiency = 0f) }
        assertFailsWith<IllegalArgumentException> { snapshot.copy(segments = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            snapshot.copy(segments = listOf(PowerSegment(10L, 7f), PowerSegment(5L, 12f)))
        }
        assertFailsWith<IllegalArgumentException> { ChargingCalculator.minutesToReach(snapshot, 0L, 101f) }
        assertFailsWith<IllegalArgumentException> { ChargingCalculator.predict(snapshot, 0L, -1.0) }
    }

    @Test
    fun configurationCanRepresentTargetBelowStartWithoutCrashing() {
        val belowStart = snapshot.copy(targetPercent = 10f)
        assertEquals(0.0, ChargingCalculator.minutesToReach(belowStart, belowStart.startTime, 10f))
        assertEquals(20f, ChargingCalculator.percentAt(belowStart, belowStart.startTime))
    }

    @Test
    fun calculateKwh_typicalValues() {
        // 60 kWh battery at 50% = 30 kWh
        assertEquals(30, ChargingCalculator.calculateKwh(60f, 50f))
        
        // 75 kWh battery at 80% = 60 kWh
        assertEquals(60, ChargingCalculator.calculateKwh(75f, 80f))
        
        // 100 kWh battery at 25% = 25 kWh
        assertEquals(25, ChargingCalculator.calculateKwh(100f, 25f))
    }

    @Test
    fun calculateKwh_boundaryValues() {
        // 0% should return 0
        assertEquals(0, ChargingCalculator.calculateKwh(60f, 0f))
        
        // 100% should return full capacity
        assertEquals(60, ChargingCalculator.calculateKwh(60f, 100f))
        assertEquals(75, ChargingCalculator.calculateKwh(75f, 100f))
    }

    @Test
    fun calculateKwh_smallBatteryCapacity() {
        // Small battery: 20 kWh at 50% = 10 kWh
        assertEquals(10, ChargingCalculator.calculateKwh(20f, 50f))
        
        // Very small battery: 10 kWh at 30% = 3 kWh
        assertEquals(3, ChargingCalculator.calculateKwh(10f, 30f))
    }

    @Test
    fun calculateKwh_largeBatteryCapacity() {
        // Large battery: 120 kWh at 80% = 96 kWh
        assertEquals(96, ChargingCalculator.calculateKwh(120f, 80f))
        
        // Very large battery: 200 kWh at 50% = 100 kWh
        assertEquals(100, ChargingCalculator.calculateKwh(200f, 50f))
    }

    @Test
    fun calculateKwh_rounding() {
        // Test rounding: 60 kWh at 33% = 19.8 -> rounds to 20
        assertEquals(20, ChargingCalculator.calculateKwh(60f, 33f))
        
        // 60 kWh at 32% = 19.2 -> rounds to 19
        assertEquals(19, ChargingCalculator.calculateKwh(60f, 32f))
        
        // 75 kWh at 33% = 24.75 -> rounds to 25
        assertEquals(25, ChargingCalculator.calculateKwh(75f, 33f))
    }

    @Test
    fun calculateKwh_decimalPercentages() {
        // 60 kWh at 50.5% = 30.3 -> rounds to 30
        assertEquals(30, ChargingCalculator.calculateKwh(60f, 50.5f))
        
        // 60 kWh at 50.9% = 30.54 -> rounds to 31
        assertEquals(31, ChargingCalculator.calculateKwh(60f, 50.9f))
    }
}
