package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.ChargingCalculation
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.RunningViewEvent
import info.anodsplace.evtimer.data.RunningViewState
import info.anodsplace.evtimer.data.TimePrediction
import info.anodsplace.evtimer.data.TimeProgress
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RunningCompactLayoutTest {
    @Test
    fun narrowLight() = verifyScreen(320, 720, 1f, dark = false)

    @Test
    fun narrowDark() = verifyScreen(320, 720, 1f, dark = true)

    @Test
    fun narrowLargeTextLight() = verifyScreen(320, 720, 2f, dark = false)

    @Test
    fun narrowLargeTextDark() = verifyScreen(320, 720, 2f, dark = true)

    @Test
    fun regularLight() = verifyScreen(412, 840, 1f, dark = false)

    @Test
    fun regularDark() = verifyScreen(412, 840, 1f, dark = true)

    @Test
    fun narrowLargeTextRtl() = verifyScreen(320, 720, 2f, dark = false, direction = LayoutDirection.Rtl)

    @Test
    fun editableValueMovesBelowItsLabelWithLargeText() = runDesktopComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                AppTheme(dynamicColor = false) {
                    EditableStatRow("Charging Speed", "3.6 kW", "Edit", onClick = {}, Modifier.width(240.dp))
                }
            }
        }
        val label = onNodeWithText("Charging Speed", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = onNodeWithText("3.6 kW", useUnmergedTree = true)
        value.assertFullyRendered(maxLines = 1)
        assertTrue(value.fetchSemanticsNode().boundsInRoot.top >= label.bottom)
    }

    @Test
    fun statValueMovesBelowItsLabelInsteadOfBeingSqueezed() = runDesktopComposeUiTest {
        setContent {
            AppTheme(dynamicColor = false) {
                // 320dp screen minus the screen and card's horizontal padding.
                StatRow("Time Remaining", "25h 45m \u00b7 Tomorrow, 11:45 PM", Modifier.width(240.dp))
            }
        }
        val label = onNodeWithText("Time Remaining").fetchSemanticsNode().boundsInRoot
        val value = onNodeWithText("25h 45m \u00b7 Tomorrow, 11:45 PM")
        value.assertFullyRendered()
        assertTrue(value.fetchSemanticsNode().boundsInRoot.top >= label.bottom)
    }

    @Test
    fun shortStatValuesStayBesideTheirLabelsWhenTheyFit() = runDesktopComposeUiTest {
        setContent {
            AppTheme(dynamicColor = false) {
                StatRow("Elapsed Time", "5m", Modifier.width(332.dp))
            }
        }
        val label = onNodeWithText("Elapsed Time").fetchSemanticsNode().boundsInRoot
        val value = onNodeWithText("5m").fetchSemanticsNode().boundsInRoot
        assertEquals(label.top, value.top)
        assertTrue(value.left > label.right)
    }

    @Test
    fun longTimelineEndpointsWrapInsteadOfCompetingForWidth() = runDesktopComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                AppTheme(dynamicColor = false) {
                    TimePredictionBar(
                        progress = TimeProgress(0.1f, emptyList()),
                        startTimeText = "8:00 PM",
                        estimatedEndTimeText = "Tomorrow, 11:45 PM",
                        modifier = Modifier.width(288.dp)
                    )
                }
            }
        }
        val start = onNodeWithText("8:00 PM").fetchSemanticsNode().boundsInRoot
        val end = onNodeWithText("Tomorrow, 11:45 PM")
        end.assertFullyRendered()
        assertTrue(end.fetchSemanticsNode().boundsInRoot.top >= start.bottom)
    }

    private fun verifyScreen(
        width: Int,
        height: Int,
        fontScale: Float,
        dark: Boolean,
        direction: LayoutDirection = LayoutDirection.Ltr
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val endTime = "Tomorrow, 11:45 PM"
        val state = RunningViewState(
            isLoading = false,
            settings = ChargingSettings(75f, 3.6f, 20f, 80f),
            calculation = ChargingCalculation(
                timeRemainingMinutes = 1545,
                estimatedPercent = 45f,
                chargingSpeed = 3.6f
            ),
            elapsedMinutes = 30,
            startTimeText = "8:00 PM",
            estimatedEndTimeText = endTime,
            timeProgress = TimeProgress(
                0.1f, listOf(TimePrediction(1, 0.2f, 50f), TimePrediction(2, 0.3f, 55f))
            )
        )
        val events = mutableListOf<RunningViewEvent>()
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides direction
            ) {
                AppTheme(darkTheme = dark, dynamicColor = false) {
                    Surface {
                        Box(Modifier.fillMaxSize().testTag("screen")) {
                            RunningTimerScreen(state, onEvent = events::add)
                        }
                    }
                }
            }
        }
        val name = "$width-${fontScale}x-${if (dark) "dark" else "light"}-$direction"
        val directory = File("build/reports/running-layout").apply { mkdirs() }
        ImageIO.write(onNodeWithTag("screen").captureToImage().toAwtImage(), "png", File(directory, "$name-top.png"))

        // The end time must be readable before scrolling, not just exist in semantics.
        onNodeWithText(endTime).assertFullyRendered()
        onNodeWithText("Stop Timer").assertIsDisplayed()
        onNodeWithText("3.6 kW", useUnmergedTree = true).performScrollTo().assertFullyRendered(maxLines = 1)
        val remaining = onNodeWithText("25h 45m \u00b7 $endTime")
        remaining.performScrollTo()
        ImageIO.write(onNodeWithTag("screen").captureToImage().toAwtImage(), "png", File(directory, "$name-stats.png"))
        remaining.assertFullyRendered()

        onNodeWithText("Stop Timer").assertIsDisplayed().performClick()
        assertEquals(listOf<RunningViewEvent>(RunningViewEvent.StopCharging), events)
    }

    private fun SemanticsNodeInteraction.assertFullyRendered(maxLines: Int = Int.MAX_VALUE) {
        assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        val layout = layouts.single()
        val lines = 0 until layout.lineCount
        val widestLine = lines.maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
        val details = "${layout.layoutInput.text} size=${layout.size} lines=${layout.lineCount} widest=$widestLine"
        assertFalse(lines.any(layout::isLineEllipsized), "Ellipsized text: $details")
        assertTrue(layout.lineCount <= maxLines, "Text wrapped more than $maxLines line(s): $details")
        assertFalse(layout.didOverflowHeight, "Vertically clipped text: $details")
        val bounds = fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width >= widestLine - 1f, "Text is horizontally clipped: $details bounds=$bounds")
        assertTrue(bounds.height >= layout.size.height - 1f, "Text is vertically clipped by its parent: $details")
    }
}
