package info.anodsplace.evtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

class ChargingCompletionController(
    context: Context,
    chargingService: ChargingService,
    repository: ChargingRepository,
    scope: CoroutineScope
) {
    init {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notifier = ChargingCompletionNotifier(context)
        val operation = PendingIntent.getBroadcast(
            context, 0, Intent(context, ChargingCompletionReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val milestoneOperation = PendingIntent.getBroadcast(
            context, 1, Intent(context, ChargingCompletionReceiver::class.java)
                .putExtra(ChargingCompletionNotifier.EXTRA_AT_80, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scope.launch {
            var scheduledEta = 0L
            var scheduledMilestone = 0L
            combine(chargingService.status, repository.observeNotifyAt80()) { status, notifyAt80 ->
                status to notifyAt80
            }.collect { (status, notifyAt80) ->
                if (status.isLoading) return@collect
                if (!status.isRunning) {
                    alarmManager.cancel(operation)
                    notifier.cancel()
                    scheduledEta = 0L
                } else if (status.estimatedEndTime > System.currentTimeMillis() &&
                    status.estimatedEndTime != scheduledEta
                ) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, status.estimatedEndTime, operation)
                    scheduledEta = status.estimatedEndTime
                }
                if (!status.isRunning || !notifyAt80) {
                    alarmManager.cancel(milestoneOperation)
                    notifier.cancel(at80 = true)
                    scheduledMilestone = 0L
                } else {
                    val milestone = status.milestoneEstimate
                    if (milestone != null && !milestone.reached &&
                        milestone.estimatedTime > System.currentTimeMillis() &&
                        milestone.estimatedTime != scheduledMilestone
                    ) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, milestone.estimatedTime, milestoneOperation
                        )
                        scheduledMilestone = milestone.estimatedTime
                    }
                }
            }
        }
    }
}
