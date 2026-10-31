package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
