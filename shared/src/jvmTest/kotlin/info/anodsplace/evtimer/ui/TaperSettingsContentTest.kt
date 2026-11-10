package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingSettings
import kotlin.test.Test

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
}
