package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ChargingSettings

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    @Test
    fun calibrationHasNoGlobalSettingsReset() = runDesktopComposeUiTest {
        setContent {
            MaterialTheme { SettingsScreen(true, {}, {}, {}, {}) }
        }
        onNodeWithText("Reset learned estimates").assertDoesNotExist()
    }

    @Test
    fun groupedRowsOpenDestinationsAndWholeSwitchRowToggles() = runDesktopComposeUiTest {
        val notify = mutableStateOf<Boolean?>(null)
        var presets = 0
        var history = 0
        var licenses = 0
        setContent {
            MaterialTheme {
                SettingsScreen(
                    notify.value, { notify.value = it }, {}, { licenses++ }, { presets++ },
                    onOpenHistory = { history++ }
                )
            }
        }
        onNodeWithText("Notify at 80%").assertIsNotEnabled()
        runOnIdle { notify.value = true }
        onNodeWithText("Notify at 80%").assertIsOn().performClick()
        onNodeWithText("Notify at 80%").assertIsOff()
        onNodeWithText("Presets").performClick()
        onNodeWithText("Charging history").performClick()
        onNodeWithText("Keep at least one preset.").assertDoesNotExist()
        onNodeWithText("Source code").performScrollTo().assertExists()
        onNodeWithText("Privacy policy").performScrollTo().assertExists()
        onNodeWithText("Open-source licenses").performScrollTo().performClick()
        runOnIdle {
            assertEquals(1, presets)
            assertEquals(1, history)
            assertEquals(1, licenses)
        }
    }

    @Test
    fun dedicatedPresetScreenKeepsLastPresetGuardAndBack() = runDesktopComposeUiTest {
        var backed = false
        setContent {
            MaterialTheme {
                PresetManagementScreen(
                    listOf(ChargingPreset("custom", "Default", ChargingSettings.DEFAULT)),
                    { _, _ -> }, {}, { _, _ -> }, {}, { backed = true }
                )
            }
        }
        onNodeWithText("Presets").assertExists()
        onNodeWithText("Default").assertExists()
        onNodeWithContentDescription("Delete Default").assertIsNotEnabled()
        onNodeWithContentDescription("Back").performClick()
        runOnIdle { assertEquals(true, backed) }
    }
}
