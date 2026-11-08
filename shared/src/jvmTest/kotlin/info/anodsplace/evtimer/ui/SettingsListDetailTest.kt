package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import info.anodsplace.evtimer.generated.resources.Res
import info.anodsplace.evtimer.generated.resources.preset_name
import info.anodsplace.evtimer.generated.resources.settings_presets_help
import info.anodsplace.evtimer.generated.resources.settings_presets
import info.anodsplace.evtimer.generated.resources.settings_select_detail
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsListDetailTest {
    @Test
    fun expandedSettingsKeepsListBesideSelectionAndBackReturnsToPlaceholder() = runDesktopComposeUiTest(
        width = 1400,
        height = 900
    ) {
        val backStack = mutableStateListOf("settings")
        setContent {
            MaterialTheme {
                SettingsTestNavigation(backStack, 1200.dp)
            }
        }

        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithText("Choose a setting to view its details").assertIsDisplayed()
        onNodeWithText("Presets").performClick()
        onNodeWithText("Use the edit button to rename. Drag the handle to reorder.").assertIsDisplayed()
        onNodeWithText("Settings").assertIsDisplayed()
        assertEquals(1, onAllNodesWithContentDescription("Back").fetchSemanticsNodes().size)

        runOnIdle { backStack.removeLast() }
        onNodeWithText("Choose a setting to view its details").assertIsDisplayed()
        assertEquals(listOf("settings"), backStack.toList())
    }

    @Test
    fun compactSettingsPushesDetailAndResizingRetainsSelectionAndDialog() = runDesktopComposeUiTest(
        width = 1400,
        height = 900
    ) {
        val backStack = mutableStateListOf("settings")
        val width = mutableStateOf(500.dp)
        setContent {
            MaterialTheme {
                SettingsTestNavigation(backStack, width.value)
            }
        }

        onNodeWithText("Choose a setting to view its details").assertDoesNotExist()
        onNodeWithText("Presets").performClick()
        onNodeWithText("Use the edit button to rename. Drag the handle to reorder.").assertIsDisplayed()
        onNodeWithText("Settings").assertDoesNotExist()
        onNodeWithText("Preset name").assertIsDisplayed()
        assertEquals(1, onAllNodesWithContentDescription("Back").fetchSemanticsNodes().size)

        runOnIdle { width.value = 1200.dp }
        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithText("Use the edit button to rename. Drag the handle to reorder.").assertIsDisplayed()
        onNodeWithText("Preset name").assertIsDisplayed()
        assertEquals(listOf("settings", "presets"), backStack.toList())

        runOnIdle { width.value = 500.dp }
        onNodeWithText("Settings").assertDoesNotExist()
        onNodeWithText("Preset name").assertIsDisplayed()
        assertEquals(listOf("settings", "presets"), backStack.toList())
    }
}

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun SettingsTestNavigation(backStack: MutableList<String>, width: Dp) {
    Box(Modifier.width(width).height(800.dp)) {
        AdaptiveSettingsNavigation {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLast() },
                sceneStrategies = listOf(rememberSettingsListDetailSceneStrategy<String>()),
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = { key ->
                    val metadata = if (key == "settings") {
                        settingsListPaneMetadata()
                    } else {
                        settingsDetailPaneMetadata()
                    }
                    NavEntry(key, metadata = metadata) {
                        if (key == "settings") {
                            SettingsScreen(
                                notifyAt80 = true,
                                onNotifyAt80Change = {},
                                onBack = {},
                                onOpenLicenses = { backStack.add("licenses") },
                                onOpenPresets = { backStack.add("presets") },
                                onOpenHistory = { backStack.add("history") },
                                onOpenChargingPowers = { backStack.add("charging-power") },
                                selectedDetail = when (backStack.lastOrNull()) {
                                    "presets" -> SettingsItem.Presets
                                    "history" -> SettingsItem.History
                                    "charging-power" -> SettingsItem.ChargingPower
                                    "licenses" -> SettingsItem.Licenses
                                    else -> null
                                }
                            )
                        } else {
                            val showDialog = rememberSaveable { mutableStateOf(true) }
                            val detailText = stringResource(Res.string.settings_presets_help)
                            val dialogTitle = stringResource(Res.string.preset_name)
                            Box(Modifier.fillMaxSize()) {
                                SettingsHeader(
                                    title = stringResource(Res.string.settings_presets),
                                    onBack = { backStack.removeLast() },
                                    showBack = !LocalSettingsTwoPane.current
                                )
                                Text(detailText)
                            }
                            if (showDialog.value) {
                                AlertDialog(
                                    onDismissRequest = { showDialog.value = false },
                                    title = { Text(dialogTitle) },
                                    confirmButton = {}
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
