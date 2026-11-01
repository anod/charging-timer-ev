package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ChargingSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PresetManagementTest {
    @Test
    fun defaultCanBeDeletedWithConfirmationAndCancelDoesNotDelete() = runDesktopComposeUiTest {
        val presets = listOf(
            ChargingPreset("custom", "Default", ChargingSettings.DEFAULT),
            ChargingPreset("home", "Home", ChargingSettings.DEFAULT)
        )
        val deleted = mutableListOf<String>()
        setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PresetManagement(presets, { _, _ -> }, { deleted += it }, { _, _ -> })
                }
            }
        }
        onNodeWithText("Add").assertDoesNotExist()
        onNodeWithText("Edit").assertDoesNotExist()
        onNodeWithText("Capacity (kWh)").assertDoesNotExist()
        onAllNodes(hasText("Delete"))[0].performClick()
        assertEquals(emptyList(), deleted)
        onNodeWithText("Delete preset?").assertExists()
        onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), deleted)
        onAllNodes(hasText("Delete"))[0].performClick()
        onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("custom"), deleted)
    }

    @Test
    fun renameOnlyCommitsValidatedNameAndEmojiOnSave() = runDesktopComposeUiTest {
        val initial = ChargingPreset("custom", "Default", ChargingSettings.DEFAULT)
        val saved = mutableListOf<String>()
        setContent {
            MaterialTheme { PresetRenameDialog(initial, listOf(initial), {}, { saved += it }) }
        }
        onNodeWithText("Preset name").performTextReplacement("Garage")
        onNodeWithText("\uD83C\uDFE0").performClick()
        assertEquals(emptyList(), saved)
        onNodeWithText("Target %").assertDoesNotExist()
        onNodeWithText("Power (kW)").assertDoesNotExist()
        onNodeWithText("Save").performClick()
        assertEquals(listOf("Garage\uD83C\uDFE0"), saved)
    }

    @Test
    fun renameRejectsBlankLongAndDuplicateNames() = runDesktopComposeUiTest {
        val initial = ChargingPreset("custom", "Default", ChargingSettings.DEFAULT)
        setContent {
            MaterialTheme {
                PresetRenameDialog(initial, listOf(initial, initial.copy(id = "home", name = "Home")), {}, {})
            }
        }
        for (invalid in listOf("", "   ", "hOME", "x".repeat(33))) {
            onNodeWithText("Preset name").performTextReplacement(invalid)
            onNodeWithText("Save").assertIsNotEnabled()
        }
        onNodeWithText("Preset name").performTextReplacement("Garage")
        onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun lastPresetCanBeRenamedButNotDeletedOrMoved() = runDesktopComposeUiTest {
        val initial = ChargingPreset("custom", "Default", ChargingSettings.DEFAULT)
        setContent {
            MaterialTheme { PresetManagement(listOf(initial), { _, _ -> }, {}, { _, _ -> }) }
        }
        onNodeWithText("Rename").assertIsEnabled()
        onNodeWithText("Delete").assertIsNotEnabled()
        onNodeWithText("Move up").assertIsNotEnabled()
        onNodeWithText("Move down").assertIsNotEnabled()
    }
}
