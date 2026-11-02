package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingSummary
import info.anodsplace.evtimer.data.ShareResult
import info.anodsplace.evtimer.data.SummarySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EndSummaryScreenTest {
    @Test
    fun shareUsesCompactPlainTextAndDoneIsExplicit() = runDesktopComposeUiTest {
        var shared = ""
        var done = false
        val summary = ChargingSummary(20f, 80f, 11_760_000L, 36f, 11f,
            listOf(SummarySegment(11f, 11_760_000L)), "Home", false, false)
        setContent {
            MaterialTheme {
                EndSummaryScreen(summary, { shared = it; ShareResult.Copied }, { done = true })
            }
        }
        onNodeWithText("20% → 80%").assertExists()
        onNodeWithText("Charging speeds").assertExists()
        onNodeWithText("Share").performClick()
        runOnIdle { assertEquals("⚡ Charged 20% → 80% · 36 kWh · 3h 16m · avg 11 kW · Home", shared) }
        onNodeWithText("Summary copied").assertExists()
        onNodeWithText("Done").performClick()
        runOnIdle { assertTrue(done) }
    }

    @Test
    fun interruptedShareMakesMissingEstimatesExplicitWithoutPresetSuffix() = runDesktopComposeUiTest {
        var shared = ""
        val summary = ChargingSummary(20f, null, null, null, null, emptyList(), null, false, true)
        setContent {
            MaterialTheme {
                EndSummaryScreen(summary, { shared = it; ShareResult.Shared }, {})
            }
        }
        onNodeWithText("Share").performClick()
        runOnIdle {
            assertEquals(
                "⚡ Charged 20% → Unknown% · Unknown kWh · Unknown · avg Unknown kW · Interrupted by restore · end time unknown",
                shared
            )
        }
        onNodeWithText("Charging speeds").assertDoesNotExist()
    }
}
