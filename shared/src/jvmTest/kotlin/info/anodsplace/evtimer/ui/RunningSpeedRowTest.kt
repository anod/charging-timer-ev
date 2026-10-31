package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.RunningViewState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RunningSpeedRowTest {
    private val state = RunningViewState(isLoading = false)

    @Test
    fun clickingSpeedRowOrIconOpensSheetAndBigButtonIsGone() = runDesktopComposeUiTest {
        setContent { MaterialTheme { RunningTimerScreen(state, onEvent = {}) } }
        onNodeWithText("Change speed").assertDoesNotExist()
        val row = onNodeWithContentDescription("Change charging speed")
        row.assertHeightIsAtLeast(48.dp)
        assertEquals(Role.Button, row.fetchSemanticsNode().config[SemanticsProperties.Role])

        onNodeWithText("Charging Speed").performClick()
        onNodeWithText("Change speed").assertExists()
        onNodeWithText("Apply").assertExists()
    }

    @Test
    fun clickingIconOpensSheet() = runDesktopComposeUiTest {
        setContent { MaterialTheme { RunningTimerScreen(state, onEvent = {}) } }
        onNodeWithContentDescription("Change charging speed").performClick()
        onNodeWithText("Change speed").assertExists()
    }
}
