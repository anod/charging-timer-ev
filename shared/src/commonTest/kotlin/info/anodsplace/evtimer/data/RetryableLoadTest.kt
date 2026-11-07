package info.anodsplace.evtimer.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RetryableLoadTest {
    @Test
    fun failureIsReportedAndRetryRestartsTheLoad() = runTest {
        val states = mutableListOf<String>()
        var attempts = 0
        val load = RetryableLoad(backgroundScope, "Test", { states += "loading" }, { states += "error" }) {
            attempts++
            if (attempts == 1) error("test failure")
            states += "loaded"
            awaitCancellation()
        }
        load.retry()
        runCurrent()
        assertEquals(listOf("loading", "error"), states)
        load.retry()
        runCurrent()
        assertEquals(listOf("loading", "error", "loading", "loaded"), states)
        assertEquals(2, attempts)
    }

    @Test
    fun restartingACancelledLoadDoesNotReportAnError() = runTest {
        var errors = 0
        var starts = 0
        val load = RetryableLoad(backgroundScope, "Test", {}, { errors++ }) {
            starts++
            awaitCancellation()
        }
        load.retry()
        runCurrent()
        load.retry()
        runCurrent()
        assertEquals(2, starts)
        assertEquals(0, errors)
    }

    @Test
    fun failureAfterAnEmissionRemainsRetryable() = runTest {
        val states = mutableListOf<String>()
        var attempts = 0
        val load = RetryableLoad(backgroundScope, "Test", { states += "loading" }, { states += "error" }) {
            states += "value"
            if (attempts++ == 0) error("later failure")
            awaitCancellation()
        }
        load.retry()
        runCurrent()
        load.retry()
        runCurrent()
        assertEquals(listOf("loading", "value", "error", "loading", "value"), states)
    }
}
