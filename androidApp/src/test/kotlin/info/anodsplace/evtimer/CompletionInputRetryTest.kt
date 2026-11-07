package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.ChargingSessionSnapshot
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ChargingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompletionInputRetryTest {
    @Test
    fun resumedObservationsDoNotDuplicateCompletionNotifications() = runBlocking {
        val snapshot = ChargingSessionSnapshot.from(ChargingSettings(60f, 12f, 20f, 80f), 1L)
        val entry = ChargingHistoryEntry(1L, snapshot, 10_800_001L, 80f, null, false)
        var attempts = 0
        var failures = 0
        val inputs = completionInputs(
            flowOf(ChargingStatus(isLoading = false)),
            { flowOf(true) },
            {
                flow {
                    attempts++
                    emit(listOf(entry))
                    delay(10)
                    if (attempts < 3) error("history unavailable")
                    awaitCancellation()
                }
            },
            retryDelayMillis = 0,
            onFailure = { failures++ }
        )
        val notification = CompletionNotificationState()
        notification.update(true, null)
        val actions = inputs.take(3).toList().map { (status, _, latest) ->
            notification.update(status.isRunning, latest)
        }
        assertEquals(2, failures)
        assertEquals(3, attempts)
        assertEquals(listOf(CompletionNotificationAction.Post, CompletionNotificationAction.Keep,
            CompletionNotificationAction.Keep), actions)
    }

    @Test
    fun cancellationIsNotRetried() = runBlocking {
        var failures = 0
        assertFailsWith<CancellationException> {
            completionInputs(flowOf(ChargingStatus()), { flowOf(true) },
                { flow { throw CancellationException("cancelled") } },
                retryDelayMillis = 0, onFailure = { failures++ }).toList()
        }
        assertEquals(0, failures)
    }
}
