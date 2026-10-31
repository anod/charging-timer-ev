package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingCalculation
import info.anodsplace.evtimer.data.RunningViewState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RunningCompletionScreenTest {
    @Test
    fun targetReplacesTheRunningHeadlineWithoutRemovingStopTimer() = runDesktopComposeUiTest {
        val state = mutableStateOf(
            RunningViewState(
                isLoading = false,
                estimatedEndTime = 100_000L,
                calculation = ChargingCalculation(timeRemainingMinutes = 1)
            )
        )
        setContent { MaterialTheme { RunningTimerScreen(state.value, onEvent = {}) } }
        onNodeWithText("Charging in Progress").assertExists()
        onNodeWithText("Finished").assertDoesNotExist()
        runOnIdle {
            state.value = state.value.copy(calculation = ChargingCalculation(timeRemainingMinutes = 0))
        }
        onNodeWithText("Finished").assertExists()
        onNodeWithText("Charging in Progress").assertDoesNotExist()
        onNodeWithText("Stop Timer").assertExists()
    }
}
