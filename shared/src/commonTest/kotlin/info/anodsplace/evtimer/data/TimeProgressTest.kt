package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeProgressTest {
    private val start = 1_800_000_000_000L
    private val snapshot = ChargingSessionSnapshot(60f, 20f, 80f, listOf(PowerSegment(start, 12f)))
    private val end = start + 180 * 60_000L

    @Test
    fun predictionsUseSixtyAndOneHundredTwentyMinutesFromNow() {
        val progress = timeProgress(snapshot, start + 30 * 60_000L, end)
        assertEquals(1f / 6f, progress.fraction)
        assertEquals(listOf(TimePrediction(1, 0.5f, 50f), TimePrediction(2, 5f / 6f, 70f)), progress.predictions)
    }

    @Test
    fun tickAtEtaIsVisibleAndCappedAtTargetButPastEtaIsHidden() {
        val now = start + 60 * 60_000L
        assertEquals(TimePrediction(2, 1f, 80f), timeProgress(snapshot, now, end).predictions.last())
        assertEquals(listOf(1), timeProgress(snapshot, now + 1L, end).predictions.map { it.hoursAhead })
        assertTrue(timeProgress(snapshot, start + 121 * 60_000L, end).predictions.isEmpty())
        assertEquals(80f, ChargingCalculator.predict(snapshot, now, 120.0))
        assertEquals(80f, ChargingCalculator.predict(snapshot, now, 180.0))
    }

    @Test
    fun speedChangeUpdatesEtaFillPositionsAndPredictionsWithoutRewritingElapsedTime() {
        val now = start + 30 * 60_000L
        val slower = requireNotNull(snapshot.withPower(now, 6f))
        val slowerEnd = start + 330 * 60_000L
        val progress = timeProgress(slower, now, slowerEnd)
        assertEquals(30f / 330f, progress.fraction)
        assertEquals(listOf(40f, 50f), progress.predictions.map { it.percent })
        assertEquals(90f / 330f, progress.predictions.first().position)

        val faster = requireNotNull(snapshot.withPower(now, 24f))
        val fasterEnd = start + 105 * 60_000L
        assertEquals(listOf(TimePrediction(1, 90f / 105f, 70f)), timeProgress(faster, now, fasterEnd).predictions)
    }

    @Test
    fun fillIsClampedBeforeStartAtCompletionAndForZeroDuration() {
        assertEquals(0f, timeProgress(snapshot, start - 1L, end).fraction)
        assertEquals(TimeProgress(1f, emptyList()), timeProgress(snapshot, end, end))
        assertEquals(TimeProgress(1f, emptyList()), timeProgress(snapshot, end + 60_000L, end))
        assertEquals(TimeProgress(1f, emptyList()), timeProgress(snapshot.copy(targetPercent = 20f), start, start))
    }
}
