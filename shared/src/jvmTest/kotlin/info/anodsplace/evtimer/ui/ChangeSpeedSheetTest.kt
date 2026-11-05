package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ChangeSpeedSheetTest {
    @Test
    fun quickAddSelectsNewSpeedButWaitsForApply() = runDesktopComposeUiTest {
        val powers = mutableStateOf(listOf(7f, 11f))
        val added = mutableListOf<Float>()
        val applied = mutableListOf<Float>()
        setContent {
            MaterialTheme {
                ChangeSpeedSheet(
                    powers = powers.value,
                    currentPower = 7f,
                    onAddPower = {
                        added += it
                        powers.value = (powers.value + it).distinct().sorted()
                    },
                    onApply = { applied += it },
                    onDismiss = {}
                )
            }
        }
        onNodeWithContentDescription("Add custom power").performClick()
        onNodeWithText("50.0 kW").performScrollTo().performClick()
        onNodeWithText("Add").performClick()
        onNodeWithText("50.0 kW").assertIsSelected()
        runOnIdle {
            assertEquals(listOf(50f), added)
            assertEquals(emptyList(), applied)
        }
        onNodeWithText("Apply").performScrollTo().performClick()
        runOnIdle { assertEquals(listOf(50f), applied) }
    }

    @Test
    fun shortSheetWithManyPowersCanReachLateOptionAndApply() = runDesktopComposeUiTest {
        val applied = mutableListOf<Float>()
        setContent {
            MaterialTheme {
                ChangeSpeedSheet(
                    powers = (1..100).map { it.toFloat() },
                    currentPower = 7f,
                    onAddPower = {},
                    onApply = { applied += it },
                    onDismiss = {},
                    modifier = Modifier.heightIn(max = 240.dp)
                )
            }
        }
        onNodeWithText("100.0 kW").assertIsNotDisplayed()
        onNodeWithText("100.0 kW").performScrollTo().assertIsDisplayed().performClick()
        onNodeWithText("Apply").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(100f), applied)
    }

    @Test
    fun fewOptionsKeepSelectionAndApplyVisible() = runDesktopComposeUiTest {
        val applied = mutableListOf<Float>()
        setContent {
            MaterialTheme {
                ChangeSpeedSheet(
                    powers = listOf(7f, 11f, 22f),
                    currentPower = 7f,
                    onAddPower = {},
                    onApply = { applied += it },
                    onDismiss = {}
                )
            }
        }
        onNodeWithText("Apply").assertIsDisplayed().assertIsNotEnabled()
        onNodeWithText("22.0 kW").assertIsDisplayed().performClick()
        onNodeWithText("Apply").assertIsDisplayed().performClick()
        assertEquals(listOf(22f), applied)
    }
}
