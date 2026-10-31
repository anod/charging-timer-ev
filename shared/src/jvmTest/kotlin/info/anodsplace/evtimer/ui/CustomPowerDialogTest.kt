package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ConfigurationViewEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CustomPowerDialogTest {
    @Test
    fun allQuickPicksReplaceTheEditableValueWithoutConfirming() = runDesktopComposeUiTest {
        val confirmed = mutableListOf<Float>()
        var dismissals = 0
        setContent {
            MaterialTheme {
                CustomPowerDialog(onDismiss = { dismissals++ }, onConfirm = { confirmed += it })
            }
        }
        onNodeWithText("Add").assertIsNotEnabled()
        onNodeWithText("Power (kW)").performTextReplacement("12.5")
        onNodeWithText("AC").assertExists()
        onNodeWithText("DC").assertExists()
        for (suggestion in listOf("2.3", "3.6", "7", "11", "22", "50", "100", "150")) {
            onNodeWithText("$suggestion kW").performScrollTo().performClick().assertIsSelected()
            assertEquals(suggestion,
                onNodeWithText("Power (kW)").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            onNodeWithText("Add").assertIsEnabled()
            assertEquals(emptyList(), confirmed)
            assertEquals(0, dismissals)
        }
        onNodeWithText("Power (kW)").performScrollTo().performTextReplacement("7,2")
        onNodeWithText("Add").performClick()
        assertEquals(listOf(7.2f), confirmed)
        assertEquals(0, dismissals)
    }

    @Test
    fun manualValidationAndCancelKeepTheExistingContract() = runDesktopComposeUiTest {
        val confirmed = mutableListOf<Float>()
        var dismissals = 0
        setContent {
            MaterialTheme {
                CustomPowerDialog(onDismiss = { dismissals++ }, onConfirm = { confirmed += it })
            }
        }
        fun hasError() = onNodeWithText("Power (kW)").fetchSemanticsNode().config
            .contains(SemanticsProperties.Error)
        assertFalse(hasError())
        for (invalid in listOf("0", "-7", "abc", "NaN", "Infinity", "1e40")) {
            onNodeWithText("Power (kW)").performTextReplacement(invalid)
            onNodeWithText("Add").assertIsNotEnabled()
            assertTrue(hasError())
        }
        onNodeWithText("Power (kW)").performTextReplacement("")
        assertFalse(hasError())
        onNodeWithText("Add").assertIsNotEnabled()
        onNodeWithText("50 kW").performScrollTo().performClick()
        onNodeWithText("Add").assertIsEnabled()
        onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), confirmed)
        assertEquals(1, dismissals)
    }

    @Test
    fun configOnlyEmitsAddAfterConfirmationAndDoesNotPopulateOptions() = runDesktopComposeUiTest {
        val settings = ChargingSettings.DEFAULT
        val events = mutableListOf<ConfigurationViewEvent>()
        setContent { MaterialTheme { ChargingPowerContent(settings, onEvent = { events += it }) } }
        onNodeWithText("50.0 kW").assertDoesNotExist()
        onNodeWithText("+").performClick()
        assertEquals(emptyList(), events)
        onNodeWithText("50 kW").performScrollTo().performClick()
        assertEquals(emptyList(), events)
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Add Custom Power").assertDoesNotExist()
        assertEquals(emptyList(), events)
        onNodeWithText("11.0 kW").assertIsSelected()
        onNodeWithText("50.0 kW").assertDoesNotExist()

        onNodeWithText("+").performClick()
        assertEquals("",
            onNodeWithText("Power (kW)").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        onNodeWithText("150 kW").performScrollTo().performClick()
        onNodeWithText("Add").performClick()
        assertEquals(listOf<ConfigurationViewEvent>(ConfigurationViewEvent.AddCustomPower(150f)), events)
        onNodeWithText("Add Custom Power").assertDoesNotExist()
        assertEquals(listOf(3.6f, 7f, 11f, 22f), ChargingSettings.DEFAULT.availablePowers)
        assertEquals(11f, settings.chargingPower)
    }
}
