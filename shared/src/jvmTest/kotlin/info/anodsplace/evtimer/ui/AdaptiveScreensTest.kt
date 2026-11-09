package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import info.anodsplace.evtimer.data.ChargingCalculation
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ConfigurationViewState
import info.anodsplace.evtimer.data.RunningViewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdaptiveScreensTest {
    @Test
    fun fractionalDensityResizeKeepsConfigurationCardsOnSameRow() = runDesktopComposeUiTest(width = 3000, height = 2400) {
        val width = mutableStateOf(400.dp)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                MaterialTheme {
                    Box(Modifier.width(width.value).height(800.dp)) {
                        ConfigurationScreen(ConfigurationViewState(isLoading = false, settings = ChargingSettings.DEFAULT), {})
                    }
                }
            }
        }
        onNodeWithContentDescription("Expand Battery Capacity", substring = true).performClick()
        runOnIdle { width.value = (2325f / 2.625f).dp }
        onNodeWithContentDescription("Collapse Battery Capacity", substring = true).assertExists()
        val battery = onNodeWithTag("configuration-battery-card").fetchSemanticsNode().boundsInRoot
        val max = onNodeWithTag("configuration-max-card").fetchSemanticsNode().boundsInRoot
        assertEquals(battery.top, max.top)
        assertTrue(max.left > battery.right)
    }

    @Test
    fun fractionalDensityResizeKeepsRunningPanesOnSameRow() = runDesktopComposeUiTest(width = 3000, height = 2400) {
        val width = mutableStateOf(400.dp)
        val state = RunningViewState(
            isLoading = false,
            settings = ChargingSettings.DEFAULT,
            calculation = ChargingCalculation(timeRemainingMinutes = 90, estimatedPercent = 65f, chargingSpeed = 11f)
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                MaterialTheme {
                    Box(Modifier.width(width.value).height(800.dp)) {
                        RunningTimerScreen(state, {})
                    }
                }
            }
        }
        runOnIdle { width.value = (2325f / 2.625f).dp }
        val progress = onNodeWithTag("running-progress-pane").fetchSemanticsNode().boundsInRoot
        val stats = onNodeWithTag("running-stats-pane").fetchSemanticsNode().boundsInRoot
        assertEquals(progress.top, stats.top)
        assertTrue(stats.left > progress.right)
        onNodeWithText("Stop Timer").assertIsDisplayed()
    }

    @Test
    fun foldInnerPortraitCentersRunningPanesBelowHeader() = runDesktopComposeUiTest(width = 673, height = 841) {
        val state = RunningViewState(
            isLoading = false,
            settings = ChargingSettings.DEFAULT,
            calculation = ChargingCalculation(timeRemainingMinutes = 90, estimatedPercent = 65f, chargingSpeed = 11f)
        )
        setContent {
            MaterialTheme {
                Box(Modifier.width(673.dp).height(841.dp)) {
                    RunningTimerScreen(state, {})
                }
            }
        }
        val heading = onNodeWithText("Charging in Progress").fetchSemanticsNode().boundsInRoot
        val progress = onNodeWithTag("running-progress-pane").fetchSemanticsNode().boundsInRoot
        val stats = onNodeWithTag("running-stats-pane").fetchSemanticsNode().boundsInRoot
        assertTrue(heading.top < 100f)
        assertEquals(progress.top, stats.top)
        assertTrue(stats.left > progress.right)
        assertTrue(progress.top > 100f)
        assertTrue(stats.bottom < 741f)
        onNodeWithText("Stop Timer").assertIsDisplayed()
    }

    @Test
    fun configurationReflowsWithoutLosingCardExpansion() = runDesktopComposeUiTest(width = 1200, height = 900) {
        val width = mutableStateOf(400.dp)
        setContent {
            MaterialTheme {
                Box(Modifier.width(width.value).height(800.dp)) {
                    ConfigurationScreen(ConfigurationViewState(isLoading = false, settings = ChargingSettings.DEFAULT), {})
                }
            }
        }
        onNodeWithContentDescription("Expand Battery Capacity", substring = true).performClick()
        runOnIdle { width.value = 1001.dp }
        onNodeWithContentDescription("Collapse Battery Capacity", substring = true).assertExists()
        onNodeWithText("Start Timer").assertIsDisplayed()
        val battery = onNodeWithContentDescription("Collapse Battery Capacity", substring = true).fetchSemanticsNode().boundsInRoot
        val max = onNodeWithContentDescription("Expand Max", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(max.left > battery.left)
    }

    @Test
    fun runningReflowsWithoutReplacingTimerControls() = runDesktopComposeUiTest(width = 1200, height = 900) {
        val width = mutableStateOf(400.dp)
        val state = RunningViewState(
            isLoading = false,
            settings = ChargingSettings.DEFAULT,
            calculation = ChargingCalculation(timeRemainingMinutes = 90, estimatedPercent = 65f, chargingSpeed = 11f)
        )
        setContent {
            MaterialTheme {
                Box(Modifier.width(width.value).height(800.dp)) {
                    RunningTimerScreen(state, {})
                }
            }
        }
        runOnIdle { width.value = 1001.dp }
        val heading = onNodeWithText("Charging in Progress").fetchSemanticsNode().boundsInRoot
        val speed = onNodeWithText("Charging Power", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(speed.left > heading.left)
        onNodeWithText("Stop Timer").assertIsDisplayed()
        runOnIdle { width.value = 400.dp }
        onNodeWithText("Charging in Progress").assertIsDisplayed()
        onNodeWithText("Stop Timer").assertIsDisplayed()
    }
}
