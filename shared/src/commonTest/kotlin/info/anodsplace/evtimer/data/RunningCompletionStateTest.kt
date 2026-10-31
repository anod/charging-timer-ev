package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunningCompletionStateTest {
    @Test
    fun onlyTheReachedTargetIsFinished() {
        val running = RunningViewState(
            isLoading = false,
            estimatedEndTime = 100_000L,
            settings = ChargingSettings(maxPercent = 100f),
            calculation = ChargingCalculation(timeRemainingMinutes = 1, estimatedPercent = 99.99f)
        )
        assertFalse(running.isFinished)
        assertFalse(running.copy(calculation = ChargingCalculation(20, 80f)).isFinished)
        assertFalse(running.copy(calculation = ChargingCalculation(1, 100f)).isFinished)
        assertFalse(running.copy(calculation = ChargingCalculation(0, 99.99f)).isFinished)
        val finished = running.copy(calculation = ChargingCalculation(0, 100f))
        assertTrue(finished.isFinished)
        assertFalse(finished.copy(isLoading = true).isFinished)
        assertFalse(finished.copy(estimatedEndTime = 0L).isFinished)
        assertFalse(RunningViewState().isFinished)
    }
}
