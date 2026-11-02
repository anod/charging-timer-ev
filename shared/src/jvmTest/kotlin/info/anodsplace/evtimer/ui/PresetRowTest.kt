package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ConfigurationViewEvent
import info.anodsplace.evtimer.data.ConfigurationViewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PresetRowTest {
    @Test
    fun renamedDefaultShowsStoredNameAndUsesEditedPresetActions() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT
        val state = ConfigurationViewState(isLoading = false,
            settings = settings.withRemovedPower(11f),
            presets = listOf(ChargingPreset("custom", "Garage", settings)))
        val events = mutableListOf<ConfigurationViewEvent>()
        setContent { MaterialTheme { ConfigurationScreen(state, { events += it }) } }
        onNodeWithText("Default").assertDoesNotExist()
        onNodeWithText("Garage *").performClick()
        onNodeWithText("Update preset").performClick()
        assertEquals(listOf<ConfigurationViewEvent>(ConfigurationViewEvent.UpdatePreset), events)
    }

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
            assertEquals(ConfigurationViewEvent.SaveAsPreset("\u26A1 Highway"), events.last())
        }
    }

    @Test
    fun nameDialogOnlyShowsErrorStateForDisplayedValidationErrors() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT
        val state = ConfigurationViewState(
            isLoading = false, settings = settings,
            presets = listOf(ChargingPreset("custom", "Default", settings))
        )
        setContent { MaterialTheme { ConfigurationScreen(state, onEvent = {}) } }
        onNodeWithContentDescription("Save as new preset").performClick()
        fun hasError() = onNodeWithText("Preset name").fetchSemanticsNode().config
            .contains(SemanticsProperties.Error)
        assertFalse(hasError())
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Preset name").performTextReplacement("   ")
        assertFalse(hasError())
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Preset name").performTextReplacement("default")
        assertTrue(hasError())
        onNodeWithText("A preset with this name already exists").assertExists()
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Preset name").performTextReplacement("")
        assertFalse(hasError())
        onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun familyEmojiPresetNameIsPreservedInChipAndDialogSemanticsAndSaveEvent() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val familyName = "Family $family"
        val state = mutableStateOf(ConfigurationViewState(
            isLoading = false, settings = settings,
            presets = listOf(
                ChargingPreset("custom", "Default", settings),
                ChargingPreset("family", familyName, settings)
            )
        ))
        val events = mutableListOf<ConfigurationViewEvent>()
        setContent { MaterialTheme { ConfigurationScreen(state.value, onEvent = { events += it }) } }

        val familyChipText = onNodeWithText("Family", substring = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals(familyName, familyChipText)
        onNodeWithContentDescription("Save as new preset").performClick()
        onNodeWithText("Save").assertIsNotEnabled()
        val flag = "\uD83C\uDDEE\uD83C\uDDF1"
        val skinTone = "\uD83D\uDC4D\uD83C\uDFFD"
        val dialogName = "Weekend $family$flag$skinTone"
        onNodeWithText("Preset name").performTextInput(dialogName)
        assertEquals(dialogName,
            onNodeWithText("Preset name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(listOf<ConfigurationViewEvent>(ConfigurationViewEvent.SaveAsPreset(dialogName)), events)
        onNodeWithText("Preset name").assertDoesNotExist()
    }

    @Test
    fun placeCarAndColorQuickPicksExposeTheirTextAndAppendToTheName() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT
        val state = ConfigurationViewState(
            isLoading = false, settings = settings,
            presets = listOf(ChargingPreset("custom", "Default", settings))
        )
        val events = mutableListOf<ConfigurationViewEvent>()
        setContent { MaterialTheme { ConfigurationScreen(state, onEvent = { events += it }) } }
        onNodeWithContentDescription("Save as new preset").performClick()
        onNodeWithText("Save").assertIsNotEnabled()
        val picks = listOf("\uD83C\uDFE0", "\uD83D\uDE99", "\uD83D\uDFE2")
        var name = ""
        for (emoji in picks) {
            onNode(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(emoji))))
                .performClick()
            name += emoji
            assertEquals(name,
                onNodeWithText("Preset name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            onNodeWithText("Save").assertIsEnabled()
        }
        onNodeWithText("Save").performClick()
        assertEquals(listOf<ConfigurationViewEvent>(ConfigurationViewEvent.SaveAsPreset(name)), events)
    }
}
