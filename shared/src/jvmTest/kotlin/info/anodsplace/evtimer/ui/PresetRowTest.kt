package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ConfigurationViewEvent
import info.anodsplace.evtimer.data.ConfigurationViewState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PresetRowTest {
    @Test
    fun editingResetAndSelectionKeepSettingsPositionAndActionsUseEditedChip() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT.copy(maxPercent = 80f)
        val state = mutableStateOf(ConfigurationViewState(
            isLoading = false, settings = settings,
            presets = listOf(
                ChargingPreset("custom", "Default", settings),
                ChargingPreset("home", "Home", settings)
            ), selectedPresetId = "home"
        ))
        val events = mutableListOf<ConfigurationViewEvent>()
        setContent { MaterialTheme { ConfigurationScreen(state.value, onEvent = { events += it }) } }
        fun settingsTop() = onNodeWithContentDescription("Expand Battery Capacity. Battery: 83 kWh")
            .fetchSemanticsNode().boundsInRoot.top
        val originalTop = settingsTop()
        onNodeWithText("Home").performClick()
        assertEquals(emptyList<ConfigurationViewEvent>(), events)
        runOnIdle { state.value = state.value.copy(settings = settings.copy(chargingPower = 22f)) }
        onNodeWithText("Home *").assertExists()
        assertEquals(originalTop, settingsTop())
        onNodeWithText("Changed from Home").assertDoesNotExist()
        onNodeWithText("Save as preset").assertDoesNotExist()
        onNodeWithText("Update preset").assertDoesNotExist()
        onNodeWithText("Home *").performClick()
        onNodeWithText("Reset").performClick()
        assertEquals(listOf<ConfigurationViewEvent>(ConfigurationViewEvent.ResetPreset), events)
        runOnIdle { state.value = state.value.copy(settings = settings) }
        assertEquals(originalTop, settingsTop())
        runOnIdle { state.value = state.value.copy(settings = settings.copy(chargingPower = 22f)) }
        onNodeWithText("Home *").performClick()
        onNodeWithText("Update preset").performClick()
        assertEquals(ConfigurationViewEvent.UpdatePreset, events.last())
        onNodeWithText("Default").performClick()
        assertEquals(ConfigurationViewEvent.SelectPreset("custom"), events.last())
        runOnIdle { state.value = state.value.copy(selectedPresetId = "custom", settings = settings) }
        assertEquals(originalTop, settingsTop())
        onNodeWithContentDescription("Save as new preset").assertExists()
    }

    @Test
    fun plusOpensSaveDialogFromDefaultAndNamedPresetAndRejectsBlank() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT
        val state = mutableStateOf(ConfigurationViewState(
            isLoading = false, settings = settings,
            presets = listOf(
                ChargingPreset("custom", "Default", settings),
                ChargingPreset("home", "Home", settings)
            )
        ))
        val events = mutableListOf<ConfigurationViewEvent>()
        setContent { MaterialTheme { ConfigurationScreen(state.value, onEvent = { events += it }) } }
        for (id in listOf("custom", "home")) {
            runOnIdle { state.value = state.value.copy(selectedPresetId = id) }
            onNodeWithContentDescription("Save as new preset").performClick()
            onNodeWithText("Save").assertIsNotEnabled()
            onNodeWithText("Preset name").performTextInput("   ")
            onNodeWithText("Save").assertIsNotEnabled()
            onNodeWithText("Preset name").performTextInput("\u26A1 Highway")
            onNodeWithText("Save").performClick()
            assertEquals(ConfigurationViewEvent.SaveAsPreset("   \u26A1 Highway"), events.last())
        }
    }
}
