package info.anodsplace.evtimer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingSummary
import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.ChargingSessionSnapshot
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.summary
import kotlinx.coroutines.CompletableDeferred
import info.anodsplace.evtimer.data.ShareResult
import info.anodsplace.evtimer.data.SummarySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class EndSummaryScreenTest {
    private fun readingEntry() = ChargingHistoryEntry(
        1L, ChargingSessionSnapshot.from(ChargingSettings(60f, 6f, 20f, 90f), 1L).copy(presetId = "home"),
        3_600_001L, 30f, null, false
    )

    @Test
    fun addSaveEditClearKeepsSummaryOpenAndRetainsOriginalEstimate() = runDesktopComposeUiTest {
        val entry = mutableStateOf(readingEntry())
        var done = false
        setContent {
            MaterialTheme {
                EndSummaryScreen(entry.value.summary(), { ShareResult.Shared }, { done = true },
                    entry = entry.value, onSaveReading = { _, actual, consent ->
                        entry.value = entry.value.copy(actualPercent = actual, calibrationEnabled = consent)
                    })
            }
        }
        onNodeWithText("Add car reading").performScrollTo().performClick()
        onNodeWithText("Battery percentage").performTextReplacement(" 28,5 ")
        onNodeWithText("Read when timer stopped").assertIsOff().performClick()
        onNodeWithText("Read when timer stopped").assertIsOn()
        onNodeWithText("Save").performClick()
        onNodeWithText("Car shows").assertDoesNotExist()
        onNodeWithText("20% → 28.5%").assertExists()
        onNodeWithText("Car reading · Estimated 30%").assertExists()
        onNodeWithTag("charging-waves").assertDoesNotExist()
        runOnIdle { assertFalse(done); assertTrue(entry.value.calibrationEnabled); assertFalse(entry.value.reviewed) }
        onNodeWithText("Edit car reading").performScrollTo().performClick()
        onNodeWithText("Battery percentage").performTextReplacement("")
        onNodeWithText("Save").performClick()
        runOnIdle { assertNull(entry.value.actualPercent); assertFalse(entry.value.calibrationEnabled) }
        onNodeWithText("20% → 30%").assertExists()
        onNodeWithText("Done").performScrollTo().performClick()
        runOnIdle { assertTrue(done) }
    }

    @Test
    fun cancelDiscardsDraftAndInvalidOrNoGainReadingCannotEnableLearning() = runDesktopComposeUiTest {
        var saved: Float? = null
        var consent = true
        val entry = readingEntry()
        setContent {
            MaterialTheme {
                EndSummaryScreen(entry.summary(), { ShareResult.Shared }, {},
                    entry = entry, onSaveReading = { _, value, enabled -> saved = value; consent = enabled })
            }
        }
        onNodeWithText("Add car reading").performScrollTo().performClick()
        for (invalid in listOf("NaN", "101", "-1", "28..5")) {
            onNodeWithText("Battery percentage").performTextReplacement(invalid)
            onNodeWithText("Save").assertIsNotEnabled()
            onNodeWithText("Enter a number from 0 to 100").assertExists()
        }
        onNodeWithText("Battery percentage").performTextReplacement("28")
        onNodeWithText("Read when timer stopped").performClick()
        onNodeWithText("Cancel").performClick()
        runOnIdle { assertNull(saved) }
        onNodeWithText("Add car reading").performScrollTo().performClick()
        onNodeWithText("Battery percentage").performTextReplacement("18.5")
        onNodeWithText("Read when timer stopped").assertIsNotEnabled().assertIsOff()
        onNodeWithText("Save").performClick()
        runOnIdle { assertEquals(18.5f, saved); assertFalse(consent) }
    }

    @Test
    fun saveFailureRetainsDraftAndBusyPreventsConcurrentSaveOrCancel() = runDesktopComposeUiTest {
        val completion = CompletableDeferred<Unit>()
        var attempts = 0
        setContent {
            MaterialTheme {
                CarReadingDialog(readingEntry(), {}, { _, _, _ ->
                    attempts++
                    completion.await()
                    error("storage unavailable")
                })
            }
        }
        onNodeWithText("Battery percentage").performTextReplacement("28.5")
        onNodeWithText("Save").performClick()
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Cancel").assertIsNotEnabled()
        runOnIdle { assertEquals(1, attempts); completion.complete(Unit) }
        onNodeWithText("Could not save the reading. Try again.").assertExists()
        onNodeWithText("28.5").assertExists()
        onNodeWithText("Car shows").assertExists()
    }

    @Test
    fun interruptedHistoryReadingStaysEditableWithoutConsent() = runDesktopComposeUiTest {
        val entry = readingEntry().copy(interrupted = true, actualPercent = 28f, reviewed = true)
        setContent {
            MaterialTheme { CarReadingDialog(entry, {}, { _, _, _ -> }) }
        }
        onNodeWithText("28").assertExists()
        onNodeWithText("28.0").assertDoesNotExist()
        onNodeWithText("Read when timer stopped").assertIsNotEnabled().assertIsOff()
        onNodeWithText("Interrupted sessions are not used for calibration.")
            .assertExists()
    }

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
        onNodeWithTag("charging-waves").assertDoesNotExist()
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
