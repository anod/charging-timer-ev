package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.TimePrediction
import info.anodsplace.evtimer.data.TimeProgress
import info.anodsplace.evtimer.data.ChargingSessionSnapshot
import info.anodsplace.evtimer.data.PowerSegment
import info.anodsplace.evtimer.data.timeProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.math.roundToInt

@OptIn(ExperimentalTestApi::class)
class TimePredictionBarTest {
    @Test
    fun leftToRightTicksAlignWithTheirLabelsAndTimeEndpoints() = verifyAlignment(LayoutDirection.Ltr)

    @Test
    fun rightToLeftTicksMirrorWithTheirLabelsAndTimeEndpoints() = verifyAlignment(LayoutDirection.Rtl)

    @Test
    fun labelPlacementRejectsOverlapAndClampsEndpointOnOneRow() {
        val placement = PredictionLabelPlacement(300, 8)
        assertTrue(placement.add(0.2f, 70))
        assertFalse(placement.add(0.4f, 70))
        assertTrue(placement.add(0.6f, 70))
        assertFalse(placement.add(0.8f, 70))
        assertTrue(placement.add(1f, 70))
        assertEquals(230, predictionLabelX(1f, 70, 300))
    }

    @Test
    fun longPhoneTimelineShowsAtMostFourPredictionsThroughTarget() = verifyAdaptiveTimeline(1f, LayoutDirection.Ltr)

    @Test
    fun longPhoneTimelineUsesMeasuredLargeTextDensity() = verifyAdaptiveTimeline(2f, LayoutDirection.Ltr)

    @Test
    fun longPhoneTimelineMirrorsLargeTextWithoutOverlapping() = verifyAdaptiveTimeline(2f, LayoutDirection.Rtl)

    @Test
    fun pixelScenarioKeepsTwoHourlyTicksThroughTarget() = verifyPixelScenario(1f,
        listOf("+3h · 73%", "+6h · 86%", "+9h 15m · 100%"))

    @Test
    fun pixelScenarioKeepsLargeFontTicksThroughTarget() = verifyPixelScenario(2f,
        listOf("+3h · 73%", "+9h 15m · 100%"))

    @Test
    fun durationsRoundToNearestFiveWithHalfStepsUpAndNoZeroMinuteTarget() {
        listOf(1.0 to 5, 2.49 to 5, 7.0 to 5, 7.5 to 10, 25.0 to 25,
            47.0 to 45, 47.5 to 50, 59.0 to 60, 181.0 to 180, 554.0 to 555).forEach { (exact, rounded) ->
            assertEquals(rounded, predictionDurationMinutes(exact))
        }
    }

    @Test
    fun representativeSessionsKeepAtMostFourLabelsOnOneRowIncludingTheExactTarget() {
        val start = 1_800_000_000_000L
        for (minutes in listOf(7, 25, 60, 181, 720)) {
            val snapshot = ChargingSessionSnapshot(60f, 20f, 80f,
                listOf(PowerSegment(start, 2160f / minutes)))
            val progress = timeProgress(snapshot, start, start + minutes * 60_000L)
            for (width in listOf(80, 328, 800)) {
                val labels = requireNotNull(selectPredictionLabels(progress, width, 8) {
                    PredictionLabel(it, "resolved", 70)
                })
                assertTrue(labels.size in 1..4)
                assertEquals(progress.predictions.last(), labels.last().prediction)
                val placement = PredictionLabelPlacement(width, 8)
                assertTrue(labels.all { placement.add(it.prediction.position, it.width) })
                assertTrue(labels.dropLast(1).all { it.prediction.minutesAhead % 5.0 == 0.0 })
            }
        }
    }

    @Test
    fun roundedEndpointTextDoesNotChangeTheClockOrPercent() = runDesktopComposeUiTest {
        setContent {
            MaterialTheme {
                TimePredictionBar(TimeProgress(0f, listOf(TimePrediction(181.0, 1f, 100f))),
                    "12:00", "15:01", Modifier.width(328.dp))
            }
        }
        onNodeWithText("+3h · 100%").fetchSemanticsNode()
        onNodeWithText("15:01").fetchSemanticsNode()
    }

    @Test
    fun lazySelectionMatchesEagerSelectionAndBoundsMultiDayLabelWork() {
        val start = 1_800_000_000_000L
        for (days in listOf(20, 100, 1000)) {
            val duration = days * 1440
            val snapshot = ChargingSessionSnapshot(60f, 20f, 80f,
                listOf(PowerSegment(start, 2160f / duration)))
            val progress = timeProgress(snapshot, start, start + duration * 60_000L)
            for (labelWidth in listOf(70, 120)) {
                val evaluated = mutableListOf<TimePrediction>()
                val selected = selectPredictionLabels(progress, 328, 8) { prediction ->
                    evaluated += prediction
                    PredictionLabel(prediction, "resolved", labelWidth)
                }
                val candidates = (listOf(progress.predictions) + progress.coarserPredictions).flatMap { group ->
                    if (group.size > 2) listOf(group, group.dropLast(2) + group.last()) else listOf(group)
                }
                val expected = candidates.first { group ->
                    val placement = PredictionLabelPlacement(328, 8)
                    group.size <= 4 && group.all { placement.add(it.position, labelWidth) }
                }
                assertEquals(expected, selected?.map { it.prediction })
                assertEquals(evaluated.distinct(), evaluated, "Each label resolves only once")
                assertTrue(evaluated.size <= 32, "$days days at width $labelWidth evaluated ${evaluated.size} labels")
                assertTrue(selected!!.size <= 4)
            }
        }
    }

    @Test
    fun unresolvedLabelsStopSelectionInsteadOfChoosingAFalseFinerInterval() {
        val progress = TimeProgress(0f, listOf(TimePrediction(60.0, 0.5f, 50f),
            TimePrediction(120.0, 1f, 80f)))
        var calls = 0
        val selection = selectPredictionLabels(progress, 328, 8) { prediction ->
            calls++
            PredictionLabel(prediction, "", 0)
        }
        assertEquals(null, selection)
        assertEquals(1, calls)
    }

    @Test
    fun overwideLabelsRejectCandidatesBeforeResolvingTheirRemainingLabels() {
        val first = TimePrediction(60.0, 0.3f, 40f)
        val second = TimePrediction(120.0, 0.6f, 60f)
        val endpoint = TimePrediction(180.0, 1f, 80f)
        val progress = TimeProgress(0f, listOf(first, second, endpoint), listOf(listOf(endpoint)))
        val evaluated = mutableListOf<TimePrediction>()
        val selection = selectPredictionLabels(progress, 328, 8) { prediction ->
            evaluated += prediction
            PredictionLabel(prediction, "resolved", if (prediction == first) 400 else 70)
        }
        assertEquals(listOf(first, endpoint), evaluated)
        assertEquals(listOf(endpoint), selection?.map { it.prediction })
    }

    @Test
    fun shortSessionFormatsMinuteCheckpointsOnTheSameBar() = runDesktopComposeUiTest {
        val start = 1_800_000_000_000L
        val snapshot = ChargingSessionSnapshot(60f, 70f, 80f, listOf(PowerSegment(start, 36f)))
        val progress = timeProgress(snapshot, start, start + 10 * 60_000L)
        setContent {
            MaterialTheme {
                TimePredictionBar(progress, "12:30", "12:40", Modifier.width(328.dp))
            }
        }
        onNodeWithText("+10m · 80%").fetchSemanticsNode()
        val labels = onAllNodes(SemanticsMatcher("prediction text") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("+") } == true
        }).fetchSemanticsNodes()
        assertEquals(2, labels.size)
        assertTrue(labels.all { node -> node.config[SemanticsProperties.Text].single().text.contains("m · ") })
        assertEquals(1, onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().size)
    }

    @Test
    fun overlappingLabelsKeepOnlyTheEndpointInsteadOfAddingARow() = runDesktopComposeUiTest {
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                TimePredictionBar(
                    progress = TimeProgress(0.1f, listOf(TimePrediction(60.0, 0.5f, 45f), TimePrediction(120.0, 0.55f, 50f))),
                    startTimeText = "12:40",
                    estimatedEndTimeText = "15:40",
                    modifier = Modifier.width(300.dp)
                )
            }
        }
        onNodeWithText("+1h · 45%").assertDoesNotExist()
        onNodeWithText("+2h · 50%").fetchSemanticsNode()
    }

    private fun verifyPixelScenario(scale: Float, expected: List<String>) = runDesktopComposeUiTest {
        val start = 1_800_000_000_000L
        val snapshot = ChargingSessionSnapshot(83f, 60f, 100f, listOf(PowerSegment(start, 3.6f)))
        val eta = start + kotlin.math.ceil(
            info.anodsplace.evtimer.data.ChargingCalculator.minutesToReach(snapshot, start, 100f) * 60_000
        ).toLong()
        val progress = timeProgress(snapshot, start, eta)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.625f, scale)) {
                MaterialTheme {
                    TimePredictionBar(progress, "8:27 PM", "5:40 AM", Modifier.width(378.dp))
                }
            }
        }
        val labels = onAllNodes(SemanticsMatcher("prediction text") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("+") } == true
        }).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].single().text }
        assertEquals(expected, labels)
    }

    private fun verifyAdaptiveTimeline(scale: Float, direction: LayoutDirection) = runDesktopComposeUiTest {
        val start = 1_800_000_000_000L
        val snapshot = ChargingSessionSnapshot(60f, 20f, 80f, listOf(PowerSegment(start, 36f / 14f)))
        val progress = timeProgress(snapshot, start, start + 840 * 60_000L)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale),
                LocalLayoutDirection provides direction) {
                MaterialTheme {
                    TimePredictionBar(progress, "12:00", "02:00", Modifier.width(328.dp))
                }
            }
        }
        val matcher = SemanticsMatcher("prediction text") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("+") } == true
        }
        val nodes = onAllNodes(matcher).fetchSemanticsNodes()
        val texts = nodes.map { it.config[SemanticsProperties.Text].single().text }
        val candidates = (listOf(progress.predictions) + progress.coarserPredictions).flatMap { group ->
            if (group.size > 2) listOf(group, group.dropLast(2) + group.last()) else listOf(group)
        }
        val expectedGroup = candidates.first { group ->
            group.map { tick ->
                val minutes = predictionDurationMinutes(tick.minutesAhead)
                val duration = if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"
                "+$duration · ${tick.percent.roundToInt()}%"
            } == texts
        }
        assertTrue(texts.size in 1..4, "Expected sparse full-timeline predictions, got $texts")
        assertEquals("+14h · 80%", texts.last())
        if (texts.size > 1) assertTrue(expectedGroup.any { it.minutesAhead > 120.0 && it.minutesAhead < 840.0 })
        val bar = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        val bounds = bar.fetchSemanticsNode().boundsInRoot
        nodes.zip(expectedGroup).forEach { (node, tick) ->
            val center = bounds.left + bounds.width * if (direction == LayoutDirection.Rtl) 1f - tick.position else tick.position
            val left = (center - node.boundsInRoot.width / 2)
                .coerceIn(bounds.left, bounds.right - node.boundsInRoot.width)
            assertEquals(left, node.boundsInRoot.left, 1f)
        }
        assertEquals(1, nodes.map { it.boundsInRoot.top }.distinct().size)
        nodes.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            onNodeWithText(node.config[SemanticsProperties.Text].single().text)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val result = results.single()
            assertFalse(result.hasVisualOverflow,
                "${node.config[SemanticsProperties.Text]} size=${result.size} bounds=${node.boundsInRoot} " +
                    "lines=${result.lineCount} widthOverflow=${result.didOverflowWidth} heightOverflow=${result.didOverflowHeight}")
            assertEquals(1, results.single().lineCount)
        }
        nodes.indices.forEach { first ->
            for (second in first + 1 until nodes.size) {
                val a = nodes[first].boundsInRoot
                val b = nodes[second].boundsInRoot
                assertTrue(a.bottom <= b.top || b.bottom <= a.top || a.right + 7 <= b.left || b.right + 7 <= a.left)
            }
        }
        onNodeWithText("12:00").fetchSemanticsNode()
        onNodeWithText("02:00").fetchSemanticsNode()
        assertEquals(1, onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().size)
    }

    private fun verifyAlignment(direction: LayoutDirection) = runDesktopComposeUiTest {
        val colors = lightColorScheme()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme(colorScheme = colors) {
                    TimePredictionBar(
                        progress = TimeProgress(0.1f, listOf(TimePrediction(60.0, 0.3f, 45f), TimePrediction(120.0, 0.7f, 65f))),
                        startTimeText = "12:40",
                        estimatedEndTimeText = "15:40",
                        modifier = Modifier.width(300.dp)
                    )
                }
            }
        }
        val bar = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        val bounds = bar.fetchSemanticsNode().boundsInRoot
        val image = bar.captureToImage().toPixelMap()
        listOf("+1h · 45%" to 0.3f, "+2h · 65%" to 0.7f).forEach { (label, position) ->
            val mirrored = if (direction == LayoutDirection.Rtl) 1f - position else position
            val expectedX = bounds.left + bounds.width * mirrored
            assertEquals(expectedX, onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center.x, 1f)
            assertEquals(colors.onSurface.toArgb(), image[(image.width * mirrored).toInt(), image.height / 2].toArgb())
        }
        val start = onNodeWithText("12:40").fetchSemanticsNode().boundsInRoot
        val first = onNodeWithText("+1h · 45%").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, onNodeWithText("+2h · 65%").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        val eta = onNodeWithText("15:40").fetchSemanticsNode().boundsInRoot
        assertEquals(direction == LayoutDirection.Ltr, start.left < eta.left)
        val filledPosition = if (direction == LayoutDirection.Rtl) 0.95f else 0.05f
        assertTrue(image[(image.width * filledPosition).toInt(), image.height / 2].toArgb() == colors.primary.toArgb())
    }
}
