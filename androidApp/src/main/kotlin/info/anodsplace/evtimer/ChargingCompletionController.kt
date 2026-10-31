package info.anodsplace.evtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import info.anodsplace.evtimer.data.ChargingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ChargingCompletionController(
    context: Context,
    chargingService: ChargingService,
    scope: CoroutineScope
) {
    init {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notifier = ChargingCompletionNotifier(context)
        val operation = PendingIntent.getBroadcast(
            context, 0, Intent(context, ChargingCompletionReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        scope.launch {
            var scheduledEta = 0L
            chargingService.status.collect { status ->
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
            }
        }
    }
}
