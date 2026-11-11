package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TaperSettingsContentTest {
    @Test
    fun showsDistinctVisibleLabelsForTaperBounds() = runDesktopComposeUiTest {
        setContent {
            MaterialTheme {
                TaperSettingsContent(ChargingSettings.DEFAULT) {}
            }
        }

        onNodeWithText("Cap starts at").fetchSemanticsNode()
        onNodeWithText("Cap ends at").fetchSemanticsNode()
        onNodeWithText("22.0").fetchSemanticsNode()
    }

    @Test
    fun applyStaysCenteredOnPowerFieldWhenValidationAppears() = runDesktopComposeUiTest {
        setContent {
            MaterialTheme {
                TaperSettingsContent(ChargingSettings.DEFAULT) {}
            }
        }

        fun centerY(text: String) = onNodeWithText(text).fetchSemanticsNode().boundsInRoot.center.y

        assertEquals(centerY("22.0"), centerY("Apply"), 1f)
        onNodeWithText("22.0").performTextReplacement("0")
        onNodeWithText("Enter a power greater than 0.").fetchSemanticsNode()
        assertEquals(centerY("0"), centerY("Apply"), 1f)
    }
}
