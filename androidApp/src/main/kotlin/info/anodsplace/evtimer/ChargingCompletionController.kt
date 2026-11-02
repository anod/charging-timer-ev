package info.anodsplace.evtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.ChargingHistoryRepository
import info.anodsplace.evtimer.data.ChargingRepository
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChargingCompletionController(
    context: Context,
    private val chargingService: ChargingService,
    private val repository: ChargingRepository,
    scope: CoroutineScope,
    private val history: ChargingHistoryRepository
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifier = ChargingCompletionNotifier(context)
    private val operation = PendingIntent.getBroadcast(
        context, 0, Intent(context, ChargingCompletionReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )
    private val milestoneOperation = PendingIntent.getBroadcast(
        context, 1, Intent(context, ChargingCompletionReceiver::class.java)
            .putExtra(ChargingCompletionNotifier.EXTRA_AT_80, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    private var scheduledEta: Long? = null
    private var scheduledMilestone: Long? = null
    private val completionNotification = CompletionNotificationState()

    internal fun postCompletion() = notifier.post()

    init {
        scope.launch {
            combine(chargingService.status, repository.observeNotifyAt80(), history.observeHistory()) { status, notifyAt80, entries ->
                Triple(status, notifyAt80, entries.firstOrNull())
            }.collect { (status, notifyAt80, latest) ->
                if (status.isLoading) return@collect
                update(status, notifyAt80, latest)
            }
        }
    }

    suspend fun restore(): ChargingStatus {
        chargingService.awaitRestoreStatus()
        val notifyAt80 = repository.observeNotifyAt80().first()
        // A fresh history query follows the persisted stop, not the presentation-only summary state.
        val latest = history.observeHistory().first().firstOrNull()
        val status = chargingService.status.value
        update(status, notifyAt80, latest, restoring = true)
        return status
    }

    private fun update(
        status: ChargingStatus,
        notifyAt80: Boolean,
        latest: ChargingHistoryEntry?,
        restoring: Boolean = false
    ) {
        val schedule = ChargingAlarmSchedule.from(status, notifyAt80, System.currentTimeMillis())
        val notificationAction = completionNotification.update(status.isRunning, latest, restoring)
        if (!status.isRunning) {
            when (notificationAction) {
                CompletionNotificationAction.Post -> postCompletion()
                CompletionNotificationAction.Cancel -> notifier.cancel()
                CompletionNotificationAction.Keep -> Unit
            }
        }
        if (!status.isRunning || !notifyAt80) notifier.cancel(at80 = true)
        if (schedule.completionTime == null) {
            // An inexact alarm may still be waiting for delivery after its ETA.
            if (restoring || !status.isRunning) alarmManager.cancel(operation)
        } else if (restoring || schedule.completionTime != scheduledEta) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, schedule.completionTime, operation)
        }
        scheduledEta = schedule.completionTime
        if (schedule.milestoneTime == null) {
            if (restoring || !status.isRunning || !notifyAt80) alarmManager.cancel(milestoneOperation)
        } else if (restoring || schedule.milestoneTime != scheduledMilestone) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, schedule.milestoneTime, milestoneOperation)
        }
        scheduledMilestone = schedule.milestoneTime
    }
}

internal suspend fun ChargingService.awaitRestoreStatus(): ChargingStatus {
    status.first { !it.isLoading }
    refresh()
    // Cold overdue recompute persists completion asynchronously; wait before querying history.
    return status.first { !it.isLoading && (!it.isRunning || it.estimatedEndTime > System.currentTimeMillis()) }
}

internal data class ChargingAlarmSchedule(
    val completionTime: Long?,
    val milestoneTime: Long?
) {
    companion object {
        fun from(status: ChargingStatus, notifyAt80: Boolean, now: Long): ChargingAlarmSchedule {
            if (status.isLoading || !status.isRunning) return ChargingAlarmSchedule(null, null)
            val overdue = status.estimatedEndTime <= now
            return ChargingAlarmSchedule(
                completionTime = status.estimatedEndTime.takeIf { !overdue },
                milestoneTime = status.milestoneEstimate?.takeIf {
                    notifyAt80 && !overdue && !it.reached && it.estimatedTime > now
                }?.estimatedTime
            )
        }
    }
}
