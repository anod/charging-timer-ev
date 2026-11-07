package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ConfigurationViewState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ConfigurationCardExpansionTest {
    @Test
    fun expandedCardSurvivesConfigurationEntryRecreation() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT
        val viewState = ConfigurationViewState(
            isLoading = false,
            settings = settings,
            presets = listOf(
                ChargingPreset("custom", "Default", settings),
                ChargingPreset("home", "Home", settings)
            )
        )
        val visible = mutableStateOf(true)
        setContent {
            val stateHolder = rememberSaveableStateHolder()
            MaterialTheme {
                if (visible.value) {
                    stateHolder.SaveableStateProvider("configuration") {
                        ConfigurationScreen(viewState = viewState, onEvent = {})
                    }
                }
            }
        }

        onNodeWithContentDescription("Expand Battery Capacity", substring = true).performClick()
        onNodeWithContentDescription("Collapse Battery Capacity", substring = true).assertExists()

        runOnIdle { visible.value = false }
        runOnIdle { visible.value = true }
        onNodeWithContentDescription("Collapse Battery Capacity", substring = true).assertExists()
    }

    @Test
    fun savedExpansionIsNotAppliedAfterSelectedPresetChangesWhileAway() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT
        val viewState = mutableStateOf(ConfigurationViewState(
            isLoading = false,
            settings = settings,
            presets = listOf(
                ChargingPreset("custom", "Default", settings),
                ChargingPreset("home", "Home", settings)
            )
        ))
        val visible = mutableStateOf(true)
        setContent {
            val stateHolder = rememberSaveableStateHolder()
            MaterialTheme {
                if (visible.value) {
                    stateHolder.SaveableStateProvider("configuration") {
                        ConfigurationScreen(viewState = viewState.value, onEvent = {})
                    }
                }
            }
        }

        onNodeWithContentDescription("Expand Battery Capacity", substring = true).performClick()
        onNodeWithContentDescription("Collapse Battery Capacity", substring = true).assertExists()

        runOnIdle {
            visible.value = false
            viewState.value = viewState.value.copy(selectedPresetId = "home")
        }
        runOnIdle { visible.value = true }
        onNodeWithContentDescription("Expand Battery Capacity", substring = true).assertExists()
        onNodeWithContentDescription("Collapse Battery Capacity", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("Collapse Start", substring = true).assertExists()
    }
}
