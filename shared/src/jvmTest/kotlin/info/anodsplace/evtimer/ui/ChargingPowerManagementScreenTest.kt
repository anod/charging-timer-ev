package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ChargingPowerManagementScreenTest {
    @Test
    fun addingAndRemovingUsesCallbacksAndKeepsLastSpeedAndBack() = runDesktopComposeUiTest {
        val settings = mutableStateOf(ChargingSettings(chargingPower = 11f, availablePowers = listOf(7f, 11f)))
        val added = mutableListOf<Float>()
        val removed = mutableListOf<Float>()
        var backs = 0
        setContent {
            MaterialTheme {
                ChargingPowerManagementScreen(
                    powers = settings.value.availablePowers,
                    selectedPower = settings.value.chargingPower,
                    onAddPower = {
                        added += it
                        settings.value = settings.value.withAddedPower(it)
                    },
                    onRemovePower = {
                        removed += it
                        val selected = settings.value.chargingPower == it
                        settings.value = settings.value.withRemovedPower(it)
                        selected
                    },
                    onUndoRemovePower = { power, selected ->
                        settings.value = settings.value.withRestoredPower(power, selected)
                    },
                    onBack = { backs++ }
                )
            }
        }
        onNodeWithText("Selected").assertExists()
        onNodeWithText("Add Custom Power").performClick()
        onNodeWithText("50.0 kW").performScrollTo().performClick()
        onNodeWithText("Cancel").performClick()
        runOnIdle { assertEquals(emptyList(), added) }
        onNodeWithText("Add Custom Power").performClick()
        onNodeWithText("50.0 kW").performScrollTo().performClick()
        onNodeWithText("Add").performClick()
        runOnIdle {
            assertEquals(listOf(50f), added)
            assertEquals(50f, settings.value.chargingPower)
        }
        onNodeWithContentDescription("Remove 50.0 kW").performScrollTo().performClick()
        runOnIdle { assertEquals(7f, settings.value.chargingPower) }
        onNodeWithText("Undo").performClick()
        runOnIdle {
            assertEquals(50f, settings.value.chargingPower)
            assertEquals(listOf(7f, 11f, 50f), settings.value.availablePowers)
        }
        onNodeWithContentDescription("Remove 50.0 kW").performClick()
        onNodeWithContentDescription("Remove 11.0 kW").performClick()
        onNodeWithContentDescription("Remove 7.0 kW").assertIsNotEnabled()
        runOnIdle { assertEquals(listOf(50f, 50f, 11f), removed) }
        onNodeWithContentDescription("Back").performClick()
        runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun shortScreenCanScrollToLateSpeedAndKeepBackVisible() = runDesktopComposeUiTest {
        val removed = mutableListOf<Float>()
        setContent {
            MaterialTheme {
                ChargingPowerManagementScreen(
                    powers = (1..30).map { it.toFloat() },
                    selectedPower = 7f,
                    onAddPower = {},
                    onRemovePower = { removed += it; false },
                    onUndoRemovePower = { _, _ -> },
                    onBack = {},
                    modifier = Modifier.heightIn(max = 320.dp)
                )
            }
        }
        onNodeWithContentDescription("Remove 30.0 kW").performScrollTo().performClick()
        onNodeWithContentDescription("Back").assertExists()
        runOnIdle { assertEquals(listOf(30f), removed) }
    }
}
