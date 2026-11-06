package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.ConfigurationViewState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CollapsibleButtonTargetTest {
    @Test
    fun expandAndCollapseActionsKeepFortyEightDpTargets() = runDesktopComposeUiTest {
        val expanded = mutableStateOf(false)
        setContent {
            MaterialTheme {
                CollapsibleSettingCard(
                    title = "Capacity",
                    summary = { "summary" },
                    viewState = ConfigurationViewState(isLoading = false),
                    expanded = expanded.value,
                    onExpandedChange = { expanded.value = it },
                    expandedContent = {}
                )
            }
        }

        onNodeWithContentDescription("Expand Capacity. summary")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        onNodeWithContentDescription("Collapse Capacity. summary")
            .assertHeightIsAtLeast(48.dp)
    }
}
