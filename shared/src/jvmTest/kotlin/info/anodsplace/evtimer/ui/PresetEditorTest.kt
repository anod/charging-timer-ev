package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ChargingSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PresetEditorTest {
    @Test
    fun deletingRequiresConfirmationAndCancelLeavesPresetUntouched() = runDesktopComposeUiTest {
        val presets = listOf(
            ChargingPreset("custom", "Default", ChargingSettings.DEFAULT),
            ChargingPreset("home", "Home", ChargingSettings.DEFAULT)
        )
        val deleted = mutableListOf<String>()
        setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PresetManagement(presets, {}, { deleted += it }, { _, _ -> })
                }
            }
        }
        onNodeWithText("Move up").assertIsNotEnabled()
        onNodeWithText("Move down").assertIsNotEnabled()
        onNodeWithText("Delete").performClick()
        assertEquals(emptyList(), deleted)
        onNodeWithText("Delete preset?").assertExists()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Delete preset?").assertDoesNotExist()
        assertEquals(emptyList(), deleted)
        onNodeWithText("Delete").performClick()
        onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("home"), deleted)
    }

    @Test
    fun editsNameValuesAndRemovesPowerOnlyOnSave() = runDesktopComposeUiTest {
        val initial = ChargingPreset("home", "Home", ChargingSettings.DEFAULT)
        val saved = mutableListOf<ChargingPreset>()
        setContent {
            MaterialTheme {
                PresetEditor(initial, false, listOf(initial), {}, { saved += it })
            }
        }
        onNodeWithText("Preset name").performTextReplacement("Garage")
        onNodeWithText("Capacity (kWh)").performScrollTo().performTextReplacement("60")
        onNodeWithText("Target %").performScrollTo().performTextReplacement("80")
        onNodeWithContentDescription("Remove 11.0 kW").performScrollTo().performClick()
        assertEquals(emptyList(), saved)
        onNodeWithContentDescription("Remove 11.0 kW").assertDoesNotExist()
        onNodeWithText("Save").performClick()
        assertEquals(listOf(initial.copy(name = "Garage", settings = initial.settings.copy(
            batteryCapacity = 60f, maxPercent = 80f, chargingPower = 3.6f,
            availablePowers = listOf(3.6f, 7f, 22f)
        ))), saved)
    }

    @Test
    fun invalidNameAndValuesDisableSave() = runDesktopComposeUiTest {
        val initial = ChargingPreset("home", "Home", ChargingSettings.DEFAULT)
        setContent {
            MaterialTheme {
                PresetEditor(initial, false, listOf(initial, initial.copy(id = "work", name = "Work")), {}, {})
            }
        }
        onNodeWithText("Preset name").performTextReplacement("wORK")
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Preset name").performTextReplacement("Garage")
        for (invalid in listOf("0", "-1", "NaN", "Infinity")) {
            onNodeWithText("Capacity (kWh)").performScrollTo().performTextReplacement(invalid)
            onNodeWithText("Save").assertIsNotEnabled()
        }
        onNodeWithText("Capacity (kWh)").performTextReplacement("60")
        onNodeWithText("Target %").performScrollTo().performTextReplacement("101")
        onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun builtInHasNoNameInputAndLastPowerCannotBeRemoved() = runDesktopComposeUiTest {
        val initial = ChargingPreset("custom", "Default", ChargingSettings.DEFAULT.copy(availablePowers = listOf(11f)))
        setContent {
            MaterialTheme { PresetEditor(initial, false, listOf(initial), {}, {}) }
        }
        onNodeWithText("Preset name").assertDoesNotExist()
        onNodeWithContentDescription("Remove 11.0 kW").performScrollTo().assertIsNotEnabled()
    }
}
