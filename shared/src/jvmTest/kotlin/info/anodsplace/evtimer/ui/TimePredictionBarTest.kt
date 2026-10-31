package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.TimePrediction
import info.anodsplace.evtimer.data.TimeProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TimePredictionBarTest {
    @Test
    fun leftToRightTicksAlignWithTheirLabelsAndTimeEndpoints() = verifyAlignment(LayoutDirection.Ltr)

    @Test
    fun rightToLeftTicksMirrorWithTheirLabelsAndTimeEndpoints() = verifyAlignment(LayoutDirection.Rtl)

    private fun verifyAlignment(direction: LayoutDirection) = runDesktopComposeUiTest {
        val colors = lightColorScheme()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme(colorScheme = colors) {
                    TimePredictionBar(
                        progress = TimeProgress(0.1f, listOf(TimePrediction(1, 0.4f, 45f), TimePrediction(2, 0.6f, 65f))),
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
        listOf("+1h · 45%" to 0.4f, "+2h · 65%" to 0.6f).forEach { (label, position) ->
            val mirrored = if (direction == LayoutDirection.Rtl) 1f - position else position
            val expectedX = bounds.left + bounds.width * mirrored
            assertEquals(expectedX, onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center.x, 1f)
            assertEquals(colors.onSurface.toArgb(), image[(image.width * mirrored).toInt(), image.height / 2].toArgb())
        }
        val start = onNodeWithText("12:40").fetchSemanticsNode().boundsInRoot
        val eta = onNodeWithText("15:40").fetchSemanticsNode().boundsInRoot
        assertEquals(direction == LayoutDirection.Ltr, start.left < eta.left)
        val filledPosition = if (direction == LayoutDirection.Rtl) 0.95f else 0.05f
        assertTrue(image[(image.width * filledPosition).toInt(), image.height / 2].toArgb() == colors.primary.toArgb())
    }
}
