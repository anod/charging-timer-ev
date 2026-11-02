package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ChargingStatus
import info.anodsplace.evtimer.data.MilestoneEstimate
import kotlin.test.Test
import kotlin.test.assertEquals

class ChargingAlarmScheduleTest {
    private val now = 10_000L
    private val running = ChargingStatus(
        isLoading = false,
        isRunning = true,
        estimatedEndTime = 30_000L,
        milestoneEstimate = MilestoneEstimate(minutesRemaining = 1, estimatedTime = 20_000L)
    )

    @Test
    fun runningSessionRestoresBothFutureAlarms() {
        assertEquals(
            ChargingAlarmSchedule(30_000L, 20_000L),
            ChargingAlarmSchedule.from(running, notifyAt80 = true, now)
        )
    }

    @Test
    fun disabledMilestoneRestoresOnlyCompletion() {
        assertEquals(
            ChargingAlarmSchedule(30_000L, null),
            ChargingAlarmSchedule.from(running, notifyAt80 = false, now)
        )
    }

    @Test
    fun pastOrReachedMilestonesAreNotReplayed() {
        for (milestone in listOf(
            null,
            MilestoneEstimate(0, now - 1),
            MilestoneEstimate(0, now),
            MilestoneEstimate(0, now + 1, reached = true)
        )) {
            assertEquals(
                ChargingAlarmSchedule(30_000L, null),
                ChargingAlarmSchedule.from(running.copy(milestoneEstimate = milestone), notifyAt80 = true, now)
            )
        }
    }

    @Test
    fun overdueOrDueNowCompletionDoesNotSchedulePastAlarms() {
        for (eta in listOf(now - 1, now)) {
            assertEquals(
                ChargingAlarmSchedule(null, null),
                ChargingAlarmSchedule.from(running.copy(estimatedEndTime = eta), notifyAt80 = true, now)
            )
        }
    }

    @Test
    fun completionOneMillisecondAheadIsStillScheduled() {
        assertEquals(
            ChargingAlarmSchedule(now + 1, null),
            ChargingAlarmSchedule.from(running.copy(estimatedEndTime = now + 1), notifyAt80 = false, now)
        )
    }

    @Test
    fun stoppedInterruptedAndLoadingSessionsDoNotRestoreAnything() {
        for (status in listOf(
            ChargingStatus(),
            running.copy(isRunning = false),
            running.copy(isRunning = false, estimatedEndTime = now - 1),
            running.copy(isLoading = true)
        )) {
            assertEquals(
                ChargingAlarmSchedule(null, null),
                ChargingAlarmSchedule.from(status, notifyAt80 = true, now)
            )
        }
    }
}
