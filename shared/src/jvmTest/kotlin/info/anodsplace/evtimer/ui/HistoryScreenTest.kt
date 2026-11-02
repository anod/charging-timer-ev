package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.ChargingSessionSnapshot
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.formatSessionDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HistoryScreenTest {
    private val entry = ChargingHistoryEntry(
        1L, ChargingSessionSnapshot.from(ChargingSettings(60f, 12f, 20f, 80f), 1L)
            .copy(presetName = "Home"), 3_600_001L, 40f, null, true
    )

    @Test
    fun rowOpensSummaryAndDeleteRequiresConfirmationThenShowsEmptyState() = runDesktopComposeUiTest {
        val entries = mutableStateOf(listOf(entry))
        var opened: ChargingHistoryEntry? = null
        setContent {
            MaterialTheme {
                HistoryScreen(entries.value, { opened = it }, { entries.value = emptyList() }, {})
            }
        }
        onNodeWithText("Home · 20% → 40%").performClick()
        runOnIdle { assertEquals(entry, opened) }
        onNodeWithText("12 kWh · 1h 0m · avg 12 kW").assertExists()
        onNodeWithContentDescription("Delete ${formatSessionDate(1L)} · Home").performClick()
        onNodeWithText("Delete session?").assertExists()
        onNodeWithText("Cancel").performClick()
        runOnIdle { assertEquals(listOf(entry), entries.value) }
        onNodeWithContentDescription("Delete ${formatSessionDate(1L)} · Home").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("No past sessions yet").assertExists()
    }

    @Test
    fun interruptedRowDoesNotInventEnergyOrDurationAndDeleteFailureIsVisible() = runDesktopComposeUiTest {
        setContent {
            MaterialTheme {
                HistoryScreen(listOf(entry.copy(endPercent = null, interrupted = true)), {},
                    { error("Storage unavailable") }, {})
            }
        }
        onNodeWithText("Home · 20% → Unknown%").assertExists()
        onNodeWithText("Interrupted by restore · end time unknown").assertExists()
        onNodeWithText("12 kWh · 1h 0m · avg 12 kW").assertDoesNotExist()
        onNodeWithContentDescription("Delete ${formatSessionDate(1L)} · Home").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Could not delete session").assertExists()
        onNodeWithText("Home · 20% → Unknown%").assertExists()
    }
}
