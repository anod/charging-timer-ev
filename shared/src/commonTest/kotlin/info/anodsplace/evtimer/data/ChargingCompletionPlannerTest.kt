package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class ChargingCompletionPlannerTest {
    private val running = ChargingStatus(
        isLoading = false, isRunning = true, startTime = 1_000L, estimatedEndTime = 61_000L
    )

    @Test
    fun schedulesTheExactEtaAndHandlesOverdueTargets() {
        val plan = requireNotNull(ChargingCompletionPlanner.plan(running))
        assertEquals(ChargingCompletionPlan(1_000L, 61_000L), plan)
        assertEquals(61_000L, plan.triggerTime(20_000L))
        assertEquals(61_000L, plan.triggerTime(61_000L))
        assertEquals(70_000L, plan.triggerTime(70_000L))
    }

    @Test
    fun loadingStoppedAndAlreadyAlertedSessionsHaveNoAlarm() {
        assertNull(ChargingCompletionPlanner.plan(running.copy(isLoading = true)))
        assertNull(ChargingCompletionPlanner.plan(running.copy(isRunning = false)))
        assertNull(ChargingCompletionPlanner.plan(running, 1_000L))
        assertEquals(ChargingCompletionPlan(1_000L, 61_000L), ChargingCompletionPlanner.plan(running, 999L))
    }

    @Test
    fun everyEtaChangeReplacesThePlan() {
        assertEquals(
            ChargingCompletionPlan(1_000L, 31_000L),
            ChargingCompletionPlanner.plan(running.copy(estimatedEndTime = 31_000L))
        )
        assertEquals(
            ChargingCompletionPlan(1_000L, 121_000L),
            ChargingCompletionPlanner.plan(running.copy(estimatedEndTime = 121_000L))
        )
        assertEquals(
            ChargingCompletionPlan(80_000L, 141_000L),
            ChargingCompletionPlanner.plan(running.copy(startTime = 80_000L, estimatedEndTime = 141_000L), 1_000L)
        )
    }

    @Test
    fun invalidRunningEtaIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ChargingCompletionPlanner.plan(running.copy(estimatedEndTime = 0L))
        }
    }
}
