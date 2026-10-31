package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingCalculation
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.RunningViewEvent
import info.anodsplace.evtimer.data.RunningViewState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RunningQuickPicksIntegrationTest {
    @Test
    fun runningDialogPickAndCancelDoNothingAndAddPreselectsForExplicitApply() = runDesktopComposeUiTest {
        val state = mutableStateOf(RunningViewState(
            isLoading = false,
            calculation = ChargingCalculation(chargingSpeed = ChargingSettings.DEFAULT.chargingPower)
        ))
        val events = mutableListOf<RunningViewEvent>()
        setContent {
            MaterialTheme {
                RunningTimerScreen(state.value, onEvent = {
                    events += it
                    if (it is RunningViewEvent.AddPower) {
                        state.value = state.value.copy(settings = state.value.settings.copy(
                            availablePowers = (state.value.settings.availablePowers + it.powerKw).distinct().sorted()
                        ))
                    }
                })
            }
        }
        onNodeWithContentDescription("Change charging speed").performClick()
        onNodeWithText("Apply").assertIsNotEnabled()
        onNodeWithText("+").performClick()
        onNodeWithText("100 kW").performScrollTo().performClick()
        assertEquals(emptyList(), events)
        onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), events)
        onNodeWithText("Apply").assertIsNotEnabled()
        onNodeWithText("100.0 kW").assertDoesNotExist()

        onNodeWithText("+").performClick()
        onNodeWithText("150 kW").performScrollTo().performClick()
        assertEquals(emptyList(), events)
        onNodeWithText("Add").performClick()
        assertEquals(listOf<RunningViewEvent>(RunningViewEvent.AddPower(150f)), events)
        onNodeWithText("150.0 kW").assertIsSelected()
        assertEquals(ChargingSettings.DEFAULT.chargingPower, state.value.settings.chargingPower)
        onNodeWithText("Apply").assertIsEnabled().performClick()
        assertEquals(listOf<RunningViewEvent>(
            RunningViewEvent.AddPower(150f), RunningViewEvent.ChangePower(150f)
        ), events)
        assertEquals(ChargingSettings.DEFAULT.chargingPower, state.value.settings.chargingPower)
        onNodeWithText("Change speed").assertDoesNotExist()
    }
}
