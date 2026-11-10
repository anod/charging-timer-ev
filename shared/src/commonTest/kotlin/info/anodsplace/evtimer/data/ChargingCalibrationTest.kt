package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChargingCalibrationTest {
    private val home = ChargingPreset("home", "Home", ChargingSettings(60f, 6f, 20f, 90f, listOf(6f, 12f)))

    private fun entry(
        id: Long = 1L,
        actual: Float = 28f,
        presetId: String? = "home",
        power: Float = 6f,
        enabled: Boolean = true,
        endedAt: Long = 3_600_000L,
        segments: List<PowerSegment> = listOf(PowerSegment(0L, power))
    ) = ChargingHistoryEntry(
        id, ChargingSessionSnapshot.from(home.settings, 0L).copy(presetId = presetId, segments = segments),
        endedAt, 30f, actual, false, calibrationEnabled = enabled
    )

    @Test
    fun sampleUsesUncappedNominalEnergyAndIsIndependentOfExistingFactors() {
        assertEquals(0.8, ChargingCalibration.sample(entry())!!, 1e-8)
        assertEquals(0.8, ChargingCalibration.sample(entry().copy(
            snapshot = entry().snapshot.copy(efficiency = 0.7f,
                segments = listOf(PowerSegment(0L, 6f, 0.9f)))
        ))!!, 1e-8)
        val twoSpeeds = entry(actual = 35f, endedAt = 4_500_000L, segments =
            listOf(PowerSegment(0L, 6f), PowerSegment(3_600_000L, 12f)))
        assertEquals(9.0, ChargingCalculator.nominalEnergyAt(twoSpeeds.snapshot, twoSpeeds.endedAt), 1e-8)
        assertEquals(6.0, ChargingCalculator.nominalEnergyAt(twoSpeeds.snapshot, 3_600_000L), 1e-8)
        assertNull(ChargingCalibration.sample(twoSpeeds))
        assertEquals(0.0, ChargingCalculator.nominalEnergyAt(twoSpeeds.snapshot, -1L))
    }

    @Test
    fun samplesClampToSeventyThroughOneHundredPercentAndRequireEligibleReadings() {
        assertEquals(0.7, requireNotNull(ChargingCalibration.sample(entry(actual = 21f))), 1e-8)
        assertEquals(0.7, requireNotNull(ChargingCalibration.sample(entry(actual = 27f))), 1e-6)
        assertEquals(1.0, ChargingCalibration.sample(entry(actual = 30.5f)))
        assertEquals(1.0, ChargingCalibration.sample(entry(actual = 100f)))
        for (excluded in listOf(
            entry(actual = 20f), entry(actual = 0f), entry(actual = Float.NaN),
            entry().copy(actualPercent = null), entry(enabled = false),
            entry().copy(interrupted = true), entry(endedAt = 0L),
            entry(presetId = null)
        )) assertNull(ChargingCalibration.sample(excluded))
    }

    @Test
    fun ewmaIsSortedAndIndependentPerPresetAndExactSpeed() {
        val first = entry()
        val second = entry(id = 2L)
        val factors = ChargingCalibration.derive(listOf(second, first), listOf(home))
        assertEquals(0.898f, factors.factor("home", 6f), 1e-6f)
        assertEquals(2, factors.estimate("home", 6f).eligibleCount)
        assertEquals(1f, factors.factor("home", 12f))
        assertEquals(0, factors.estimate("home", 12f).eligibleCount)
        val otherSpeed = entry(id = 3L, actual = 36f, power = 12f)
        val split = ChargingCalibration.derive(listOf(first, otherSpeed), listOf(home))
        assertEquals(0.94f, split.factor("home", 6f), 1e-6f)
        assertEquals(0.94f, split.factor("home", 12f), 1e-6f)
        assertEquals(1f, split.factor("other-home", 6f))
        val exactSpeed = ChargingCalibration.derive(
            listOf(entry(power = 6.0001f)), listOf(home)
        )
        assertEquals(0.94f, exactSpeed.factor("home", 6.0001f), 1e-5f)
        assertEquals(1f, exactSpeed.factor("home", 6.0002f))
        val high = entry(id = 4L, actual = 30.5f)
        assertEquals(0.958f, ChargingCalibration.derive(listOf(first, high), listOf(home))
            .factor("home", 6f), 1e-6f)
    }

    @Test
    fun capacityAndPresetIdentityAreLearningFiltersAndEditStatusIsIgnored() {
        val edited = entry().copy(snapshot = entry().snapshot.copy(presetEdited = true))
        assertEquals(0.94f, ChargingCalibration.derive(listOf(edited),
            listOf(home.copy(name = "Renamed"))).factor("home", 6f), 1e-6f)
        assertEquals(1f, ChargingCalibration.derive(listOf(edited),
            listOf(home.copy(settings = home.settings.copy(batteryCapacity = 61f)))).factor("home", 6f))
        assertEquals(1f, ChargingCalibration.derive(listOf(edited), listOf(home.copy(id = "new-home")))
            .factor("new-home", 6f))
        assertNull(ChargingCalibration.sample(entry(presetId = null)))
    }

    @Test
    fun everyPositiveDurationSegmentMustUseOneExactSpeed() {
        val aToB = entry(segments = listOf(PowerSegment(0L, 6f), PowerSegment(1_800_000L, 12f)))
        assertEquals(CalibrationExclusion.MIXED_POWER, ChargingCalibration.exclusion(aToB))
        assertNull(ChargingCalibration.sample(aToB))
        val aToBToA = entry(endedAt = 3_600_000L, segments = listOf(
            PowerSegment(0L, 6f), PowerSegment(1_200_000L, 12f), PowerSegment(2_400_000L, 6f)
        ))
        assertEquals(CalibrationExclusion.MIXED_POWER, ChargingCalibration.exclusion(aToBToA))
        val zeroDurationTerminal = entry(segments =
            listOf(PowerSegment(0L, 6f), PowerSegment(3_600_000L, 12f)))
        assertEquals(6f, ChargingCalibration.singleSpeedPower(zeroDurationTerminal))
        assertEquals(0.8, ChargingCalibration.sample(zeroDurationTerminal)!!, 1e-8)
    }

    @Test
    fun perSegmentFactorsDriveSocEtaAndProgressButNotNominalIntegration() {
        val oneSpeed = entry().snapshot.copy(
            efficiency = 1f,
            segments = listOf(PowerSegment(0L, 6f, 0.8f))
        )
        assertEquals(525.0, ChargingCalculator.minutesToReach(oneSpeed, 0L, 90f), 0.001)
        assertEquals(450.0, ChargingCalculator.minutesToReach(oneSpeed, 0L, 80f), 0.001)
        assertEquals(450, ChargingCalculator.estimateMilestone(oneSpeed, 0L)!!.minutesRemaining)
        assertEquals(28f, ChargingCalculator.percentAt(oneSpeed, 3_600_000L), 1e-5f)
        assertEquals(6.0, ChargingCalculator.nominalEnergyAt(oneSpeed, 3_600_000L), 1e-8)
        assertEquals(60f / 525f, timeProgress(oneSpeed, 3_600_000L, 525L * 60_000L).fraction, 1e-6f)

        val twoSpeeds = oneSpeed.copy(segments = listOf(
            PowerSegment(0L, 6f, 0.8f), PowerSegment(3_600_000L, 12f, 0.9f)
        ))
        assertEquals(46f, ChargingCalculator.percentAt(twoSpeeds, 7_200_000L), 1e-5f)
        assertEquals(146.6666667, ChargingCalculator.minutesToReach(twoSpeeds, 7_200_000L, 90f), 0.001)
        assertEquals(18.0, ChargingCalculator.nominalEnergyAt(twoSpeeds, 7_200_000L), 1e-8)
        val changed = twoSpeeds.withPower(7_200_000L, 6f, 0.7f)!!
        assertEquals(listOf(0.8f, 0.9f), changed.segments.map { it.efficiency }.dropLast(1))
        assertEquals(0.7f, changed.segments.last().efficiency)
        assertFailsWith<IllegalArgumentException> { PowerSegment(0L, 6f, 1.01f) }
        val legacy = oneSpeed.copy(efficiency = 0.8f, segments = listOf(PowerSegment(0L, 6f)))
        assertEquals(28f, ChargingCalculator.percentAt(legacy, 3_600_000L), 1e-5f)
    }

    @Test
    fun calibrationNominalEnergyUsesTheFrozenTaperCap() {
        val settings = ChargingSettings(
            batteryCapacity = 60f,
            chargingPower = 50f,
            startPercent = 70f,
            maxPercent = 100f,
            taperStartPercent = 80f,
            taperEndPercent = 90f,
            taperPowerCapKw = 22f
        )
        val snapshot = ChargingSessionSnapshot.from(settings, 0L).copy(presetId = "home")
        val endedAt = (ChargingCalculator.minutesToReach(snapshot, 0L, 90f) * 60_000.0).toLong()
        val history = ChargingHistoryEntry(
            2L, snapshot, endedAt, 90f, 90f, false, calibrationEnabled = true
        )

        assertEquals(12.0, ChargingCalculator.nominalEnergyAt(snapshot, endedAt), 0.00001)
        assertEquals(1.0, ChargingCalibration.sample(history)!!, 0.00001)
    }

    @Test
    fun carReadingParserAcceptsDecimalsAndRejectsMalformedValuesWithoutClamping() {
        for (text in listOf("28", " 28.5 ", "28,5", ".5", ",5", "100.0", "0")) {
            assertEquals(text.trim().replace(',', '.').toFloat(), parseCarReading(text))
        }
        for (text in listOf("", " ", "-1", "101", "NaN", "Infinity", "1e2", "1,2.3", "28%", "1 2", "--2")) {
            assertNull(parseCarReading(text))
        }
    }
}
