package info.anodsplace.evtimer.data

data class ChargingCompletionPlan(val sessionId: Long, val targetTime: Long) {
    fun triggerTime(now: Long): Long = maxOf(targetTime, now)
}

/** Uses the service's snapshot-derived ETA, including all power segments. */
object ChargingCompletionPlanner {
    fun plan(status: ChargingStatus, alertedSessionId: Long? = null): ChargingCompletionPlan? {
        if (status.isLoading || !status.isRunning || status.startTime == alertedSessionId) return null
        require(status.estimatedEndTime > 0L) { "A running session requires a target ETA" }
        return ChargingCompletionPlan(status.startTime, status.estimatedEndTime)
    }
}
