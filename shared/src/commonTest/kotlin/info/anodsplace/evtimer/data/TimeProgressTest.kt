package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeProgressTest {
    private val start = 1_800_000_000_000L
    private val snapshot = ChargingSessionSnapshot(60f, 20f, 80f, listOf(PowerSegment(start, 12f)))
    private val end = start + 180 * 60_000L

    private fun TimeProgress.groups() = listOf(predictions) + coarserPredictions

    @Test
    fun hourlyPredictionsRetainOriginalAxisAndExtendThroughTarget() {
        val progress = timeProgress(snapshot, start + 30 * 60_000L, end)
        assertEquals(1f / 6f, progress.fraction)
        assertEquals(
            listOf(TimePrediction(60.0, 0.5f, 50f), TimePrediction(120.0, 5f / 6f, 70f),
                TimePrediction(150.0, 1f, 80f)),
            progress.groups().first { it.first().minutesAhead == 60.0 }
        )
        assertEquals((5..150 step 5).map { it.toDouble() }, progress.predictions.map { it.minutesAhead })
    }

    @Test
    fun shortMediumAndLongSessionsHaveNiceIntervalAlternativesAndExactEndpoint() {
        listOf(
            Triple(10, 2.0, listOf(2.0, 4.0, 6.0, 8.0, 10.0)),
            Triple(45, 10.0, listOf(10.0, 20.0, 30.0, 40.0, 45.0)),
            Triple(120, 30.0, listOf(30.0, 60.0, 90.0, 120.0)),
            Triple(360, 60.0, listOf(60.0, 120.0, 180.0, 240.0, 300.0, 360.0)),
            Triple(840, 120.0, listOf(120.0, 240.0, 360.0, 480.0, 600.0, 720.0, 840.0))
        ).forEach { (duration, interval, offsets) ->
            val session = snapshot.copy(segments = listOf(PowerSegment(start, 2160f / duration)))
            val progress = timeProgress(session, start, start + duration * 60_000L)
            val group = progress.groups().first { it.first().minutesAhead == interval }
            assertEquals(offsets, group.map { it.minutesAhead })
            group.forEach { tick ->
                assertEquals((tick.minutesAhead / duration).toFloat(), tick.position)
                assertEquals(20f + (60.0 * tick.minutesAhead / duration).toFloat(), tick.percent, 0.00001f)
            }
            assertEquals(TimePrediction(duration.toDouble(), 1f, 80f), progress.predictions.last())
            assertEquals(listOf(progress.predictions.last()), progress.groups().last())
        }
    }

    @Test
    fun fractionalEtaIsNotDuplicatedOrRoundedToWholeMinutes() {
        val now = start + 60 * 60_000L + 1L
        val progress = timeProgress(snapshot, now, end)
        val remaining = 120.0 - 1.0 / 60_000.0
        assertEquals(TimePrediction(remaining, 1f, 80f), progress.predictions.last())
        progress.groups().forEach { group ->
            assertEquals(1, group.count { it.position == 1f })
            assertTrue(group.dropLast(1).all { it.minutesAhead < remaining })
            assertEquals(group.map { it.minutesAhead }.distinct(), group.map { it.minutesAhead })
        }
    }

    @Test
    fun ticksNearEndpointAreSkippedAtEveryIntervalWithoutRoundingTheEndpoint() {
        val progress = timeProgress(snapshot, start, start + 181 * 60_000L)
        val hourly = progress.groups().first { it.first().minutesAhead == 60.0 }
        assertEquals(listOf(60.0, 120.0, 181.0), hourly.map { it.minutesAhead })
        assertEquals(TimePrediction(181.0, 1f, 80f), hourly.last())
        val tenMinute = progress.groups().first { it.first().minutesAhead == 10.0 }
        assertEquals(listOf(170.0, 180.0, 181.0), tenMinute.takeLast(3).map { it.minutesAhead })

        val justPastInterval = timeProgress(snapshot, start, end + 1L)
        justPastInterval.groups().forEach { group ->
            assertEquals(TimePrediction(180.0 + 1.0 / 60_000.0, 1f, 80f), group.last())
            assertTrue(group.dropLast(1).all { it.minutesAhead < 180.0 })
        }
    }

    @Test
    fun nearCompletionUsesMinuteTicksThenExistingFinishTextBelowOneMinute() {
        val progress = timeProgress(snapshot, end - 4 * 60_000L, end)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), progress.predictions.map { it.minutesAhead })
        assertTrue(timeProgress(snapshot, end - 59_999L, end).predictions.isEmpty())
        assertEquals(listOf(TimePrediction(1.0, 1f, 80f)), timeProgress(snapshot, end - 60_000L, end).predictions)
    }

    @Test
    fun speedChangeUpdatesEtaFillAndPredictionsWithoutRewritingElapsedTime() {
        val now = start + 30 * 60_000L
        val slower = requireNotNull(snapshot.withPower(now, 6f))
        val progress = timeProgress(slower, now, start + 330 * 60_000L)
        assertEquals(30f / 330f, progress.fraction)
        val hourly = progress.groups().first { it.first().minutesAhead == 60.0 }
        assertEquals(listOf(40f, 50f, 60f, 70f, 80f), hourly.map { it.percent })
        assertEquals(90f / 330f, hourly.first().position)

        val faster = requireNotNull(snapshot.withPower(now, 24f))
        val fastProgress = timeProgress(faster, now, start + 105 * 60_000L)
        assertEquals(
            listOf(TimePrediction(30.0, 60f / 105f, 50f), TimePrediction(60.0, 90f / 105f, 70f),
                TimePrediction(75.0, 1f, 80f)),
            fastProgress.groups().first { it.first().minutesAhead == 30.0 }
        )
    }

    @Test
    fun catchUpRecomputesRelativeTicksButDoesNotChangeTheOriginalAxis() {
        val now = start + 125 * 60_000L
        val progress = timeProgress(snapshot, now, end)
        assertEquals(125f / 180f, progress.fraction)
        val ticks = progress.groups().first { it.first().minutesAhead == 20.0 }
        assertEquals(listOf(20.0, 40.0, 55.0), ticks.map { it.minutesAhead })
        assertEquals(listOf(145f / 180f, 165f / 180f, 1f), ticks.map { it.position })
        assertEquals(68.333336f, ticks.first().percent, 0.00001f)
    }

    @Test
    fun configuredTargetAndEfficiencyRemainPartOfTheSnapshotCalculation() {
        val session = snapshot.copy(targetPercent = 100f, efficiency = 0.5f)
        val progress = timeProgress(session, start + 60 * 60_000L, start + 480 * 60_000L)
        val hourly = progress.groups().first { it.first().minutesAhead == 60.0 }
        assertEquals(listOf(40f, 50f, 60f, 70f, 80f, 90f, 100f), hourly.map { it.percent })
        assertEquals(TimePrediction(420.0, 1f, 100f), hourly.last())
    }

    @Test
    fun veryLongSessionsBoundCandidatesWithoutClampingTheForecastHorizon() {
        val duration = 20 * 1440
        val session = snapshot.copy(segments = listOf(PowerSegment(start, 2160f / duration)))
        val progress = timeProgress(session, start, start + duration * 60_000L)
        assertTrue(progress.groups().all { it.size <= 60 })
        assertEquals(TimePrediction(duration.toDouble(), 1f, 80f), progress.predictions.last())
        assertTrue(progress.groups().any { it.first().minutesAhead >= 1440 })
    }

    @Test
    fun fillIsClampedBeforeStartAtCompletionAndForZeroDuration() {
        val before = timeProgress(snapshot, start - 1L, end)
        assertEquals(0f, before.fraction)
        assertEquals(timeProgress(snapshot, start, end).predictions, before.predictions)
        assertEquals(TimeProgress(1f, emptyList()), timeProgress(snapshot, end, end))
        assertEquals(TimeProgress(1f, emptyList()), timeProgress(snapshot, end + 60_000L, end))
        assertEquals(TimeProgress(1f, emptyList()), timeProgress(snapshot.copy(targetPercent = 20f), start, start))
    }
}
