package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingCalculation
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ConfigurationViewState
import info.anodsplace.evtimer.data.MilestoneEstimate
import info.anodsplace.evtimer.data.RunningViewState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LocalizationFormattingTest {
    @Test
    fun configurationSummariesRenderSinglePercentSignsAndPrecisePowerLabels() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT.copy(
            chargingPower = 7.36f,
            availablePowers = listOf(7.36f, 11f, 7.4f)
        )
        setContent {
            MaterialTheme {
                ConfigurationScreen(
                    viewState = ConfigurationViewState(isLoading = false, settings = settings),
                    onEvent = {}
                )
            }
        }

        onNodeWithText("Max: 100% (83 kWh)", substring = true).assertExists()
        onNodeWithText("▾ Start").performScrollTo().performClick()
        onNodeWithText("Start: 60% (50 kWh)", substring = true).assertExists()
        onNodeWithText("▾ Charging Power").performScrollTo().performClick()
        onNodeWithText("Power: 7.36 kW", substring = true).assertExists()
        onNodeWithText("▸ Power: 7.36 kW").performScrollTo().performClick()
        onNodeWithText("7.36 kW").assertExists()
        onNodeWithText("11.0 kW").assertExists()
        onNodeWithText("7.4 kW").assertExists()
        onAllNodes(hasText("%%", substring = true)).assertCountEquals(0)
    }

    @Test
    fun runningScreenRendersPercentValuesAndMilestoneWithoutDoublePercent() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT
        val state = RunningViewState(
            isLoading = false,
            settings = settings,
            calculation = ChargingCalculation(
                timeRemainingMinutes = 120,
                estimatedPercent = 60f,
                chargingSpeed = 7.36f
            ),
            milestoneEstimate = MilestoneEstimate(minutesRemaining = 60, estimatedTime = 0L)
        )
        setContent { MaterialTheme { RunningTimerScreen(state, onEvent = {}) } }

        onNodeWithText("Time to 80%").assertExists()
        onNodeWithText("7.36 kW").assertExists()
        onAllNodes(hasText("60% (50 kWh)")).assertCountEquals(2)
        onNodeWithText("100% (83 kWh)").assertExists()
        onAllNodes(hasText("%%", substring = true)).assertCountEquals(0)
    }
}
