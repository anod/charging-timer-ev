package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.ChargingSessionSnapshot
import info.anodsplace.evtimer.data.ChargingSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class CompletionNotificationStateTest {
    private val completed = ChargingHistoryEntry(
        1L, ChargingSessionSnapshot.from(ChargingSettings(60f, 12f, 20f, 80f), 1L),
        10_800_001L, 80f, null, false
    )

    @Test
    fun coldStoppedCompletionNeitherPostsNorCancelsExistingNotification() {
        val state = CompletionNotificationState()
        repeat(3) {
            assertEquals(CompletionNotificationAction.Keep, state.update(false, completed))
        }
        assertEquals(CompletionNotificationAction.Cancel, state.update(false, completed.copy(reviewed = true)))
    }

    @Test
    fun newCompletionPostsOnceWhenStoppedStatusArrivesBeforeHistory() {
        val state = CompletionNotificationState()
        assertEquals(CompletionNotificationAction.Keep, state.update(true, null))
        assertEquals(CompletionNotificationAction.Cancel, state.update(false, null))
        assertEquals(CompletionNotificationAction.Post, state.update(false, completed))
        assertEquals(CompletionNotificationAction.Keep, state.update(false, completed))
    }

    @Test
    fun newCompletionPostsOnceWhenHistoryArrivesBeforeStoppedStatus() {
        val state = CompletionNotificationState()
        assertEquals(CompletionNotificationAction.Keep, state.update(true, null))
        assertEquals(CompletionNotificationAction.Keep, state.update(true, completed))
        assertEquals(CompletionNotificationAction.Post, state.update(false, completed))
        assertEquals(CompletionNotificationAction.Keep, state.update(false, completed))
    }

    @Test
    fun subsequentSessionCanPostWithoutRepostingInitialHistory() {
        val state = CompletionNotificationState()
        assertEquals(CompletionNotificationAction.Keep, state.update(false, completed))
        assertEquals(CompletionNotificationAction.Keep, state.update(true, completed))
        assertEquals(CompletionNotificationAction.Keep, state.update(false, completed))
        assertEquals(CompletionNotificationAction.Post, state.update(false, completed.copy(id = 2L)))
    }

    @Test
    fun manualStopInterruptedAndReviewedHistoryCancelInsteadOfPosting() {
        val state = CompletionNotificationState()
        state.update(true, null)
        listOf(
            completed.copy(endPercent = 79f),
            completed.copy(endPercent = null, interrupted = true),
            completed.copy(reviewed = true)
        ).forEach {
            assertEquals(CompletionNotificationAction.Cancel, state.update(false, it))
        }
    }
}
