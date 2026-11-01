package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ChargingSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                    PresetManagement(presets, { _, _ -> }, { deleted += it }, { _, _ -> }, {})
                }
            }
        }
        onNodeWithText("Add").assertDoesNotExist()
        onNodeWithText("Edit").assertDoesNotExist()
        onNodeWithText("Capacity (kWh)").assertDoesNotExist()
        onNodeWithContentDescription("Delete Default").performClick()
        assertEquals(emptyList(), deleted)
        onNodeWithText("Delete preset?").assertExists()
        onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), deleted)
        onNodeWithContentDescription("Delete Default").performClick()
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
            MaterialTheme { Column { PresetManagement(listOf(initial), { _, _ -> }, {}, { _, _ -> }, {}) } }
        }
        onNodeWithText("Default").assertIsEnabled().performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithContentDescription("Delete Default").assertIsNotEnabled()
        assertEquals(emptyList(), onNodeWithTag("preset-custom").fetchSemanticsNode().config[SemanticsActions.CustomActions])
        onNodeWithText("Keep at least one preset.").assertExists()
    }

    @Test
    fun compactRowsExposeOnlyValidAccessibilityMoves() = runDesktopComposeUiTest {
        val presets = listOf(
            ChargingPreset("custom", "Default", ChargingSettings.DEFAULT),
            ChargingPreset("home", "Home", ChargingSettings.DEFAULT)
        )
        val moves = mutableListOf<Pair<String, Int>>()
        setContent {
            MaterialTheme {
                Column { PresetManagement(presets, { _, _ -> }, {}, { id, delta -> moves += id to delta }, {}) }
            }
        }
        onNodeWithText("Rename").assertDoesNotExist()
        onNodeWithText("Delete").assertDoesNotExist()
        onNodeWithText("Move up").assertDoesNotExist()
        onNodeWithText("Move down").assertDoesNotExist()
        val firstActions = onNodeWithTag("preset-custom").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move down"), firstActions.map { it.label })
        val lastActions = onNodeWithTag("preset-home").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move up"), lastActions.map { it.label })
        onNodeWithTag("preset-custom").performCustomAccessibilityActionWithLabel("Move down")
        onNodeWithTag("preset-home").performCustomAccessibilityActionWithLabel("Move up")
        assertEquals(listOf("custom" to 1, "home" to -1), moves)
    }

    @Test
    fun handleDragPreviewsOrderAndCommitsOnlyOnDrop() = runDesktopComposeUiTest {
        val presets = listOf(
            ChargingPreset("custom", "Default", ChargingSettings.DEFAULT),
            ChargingPreset("home", "Home", ChargingSettings.DEFAULT)
        )
        val orders = mutableListOf<List<String>>()
        setContent {
            MaterialTheme {
                Column { PresetManagement(presets, { _, _ -> }, {}, { _, _ -> }, { orders += it }) }
            }
        }
        val rowHeight = onNodeWithTag("preset-custom").fetchSemanticsNode().boundsInRoot.height
        onNodeWithContentDescription("Drag to reorder Default", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.5f), durationMillis = 200)
        }
        runOnIdle { assertEquals(emptyList(), orders) }
        onNodeWithContentDescription("Drag to reorder Default", useUnmergedTree = true).performTouchInput { up() }
        runOnIdle { assertEquals(listOf(listOf("home", "custom")), orders) }
    }

    @Test
    fun cancelledHandleDragDoesNotCommit() = runDesktopComposeUiTest {
        val presets = listOf(
            ChargingPreset("custom", "Default", ChargingSettings.DEFAULT),
            ChargingPreset("home", "Home", ChargingSettings.DEFAULT)
        )
        val orders = mutableListOf<List<String>>()
        setContent {
            MaterialTheme {
                Column { PresetManagement(presets, { _, _ -> }, {}, { _, _ -> }, { orders += it }) }
            }
        }
        val rowHeight = onNodeWithTag("preset-custom").fetchSemanticsNode().boundsInRoot.height
        onNodeWithContentDescription("Drag to reorder Default", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.5f), durationMillis = 200)
            cancel()
        }
        runOnIdle { assertEquals(emptyList(), orders) }
        val firstTop = onNodeWithTag("preset-custom").fetchSemanticsNode().boundsInRoot.top
        val lastTop = onNodeWithTag("preset-home").fetchSemanticsNode().boundsInRoot.top
        kotlin.test.assertTrue(firstTop < lastTop)
    }

    @Test
    fun dragThresholdCancellationAndRepeatedGesturesKeepOrderConsistent() {
        val drag = PresetDragState(listOf("a", "b", "c"))
        drag.start("a")
        drag.drag(24f, 50f)
        assertEquals(listOf("a", "b", "c"), drag.order)
        drag.drag(2f, 50f)
        assertEquals(listOf("b", "a", "c"), drag.order)
        drag.cancel()
        assertEquals(listOf("a", "b", "c"), drag.order)
        assertNull(drag.finish())
        drag.start("a")
        drag.drag(500f, 50f)
        assertEquals(listOf("b", "c", "a"), drag.finish())
        assertNull(drag.finish())
        drag.start("a")
        drag.drag(-500f, 50f)
        drag.cancel()
        assertEquals(listOf("b", "c", "a"), drag.order)
        drag.start("a")
        drag.drag(500f, 50f)
        assertNull(drag.finish())
    }
}
