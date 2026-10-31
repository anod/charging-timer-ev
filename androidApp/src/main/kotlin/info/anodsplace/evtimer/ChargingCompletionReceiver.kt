package info.anodsplace.evtimer

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import info.anodsplace.evtimer.data.ChargingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ChargingCompletionReceiver : BroadcastReceiver(), KoinComponent {
    private val chargingService: ChargingService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in setOf(
                ChargingCompletionController.ACTION_COMPLETE,
                ChargingCompletionNotifier.ACTION_STOP,
                ChargingCompletionNotifier.ACTION_DISMISS,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )
        ) return
        val controller = (context.applicationContext as MainApplication).chargingCompletionController
        val sessionId = intent.getLongExtra(ChargingCompletionNotifier.EXTRA_SESSION_ID, 0L)
        if (action == ChargingCompletionNotifier.ACTION_STOP || action == ChargingCompletionNotifier.ACTION_DISMISS) {
            controller.dismiss(sessionId)
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withTimeout(8_000L) {
                    chargingService.status.first { !it.isLoading }
                    when (action) {
                        ChargingCompletionController.ACTION_COMPLETE ->
                            controller.onAlarm(sessionId, intent.getLongExtra(ChargingCompletionController.EXTRA_TARGET_TIME, 0L))
                        ChargingCompletionNotifier.ACTION_STOP -> {
                            controller.dismiss(sessionId)
                            val status = chargingService.status.value
                            if (status.isRunning && status.startTime == sessionId) chargingService.stop()
                        }
                        ChargingCompletionNotifier.ACTION_DISMISS -> controller.dismiss(sessionId)
                        else -> controller.sync(force = true)
                    }
                }
            } catch (error: TimeoutCancellationException) {
                Log.e("ChargingComplete", "Timed out handling completion action", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
