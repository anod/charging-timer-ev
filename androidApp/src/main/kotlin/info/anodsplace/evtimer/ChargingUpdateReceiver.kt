package info.anodsplace.evtimer

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ChargingUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val application = context.applicationContext as MainApplication
                val status = withTimeout(8_000L) { application.chargingCompletionController.restore() }
                if (status.isRunning && status.estimatedEndTime > System.currentTimeMillis() &&
                    !application.chargingSessionController.isDismissed(status.startTime)
                ) {
                    context.startForegroundService(Intent(context, ChargingForegroundService::class.java))
                }
            } catch (error: TimeoutCancellationException) {
                Log.e("ChargingRestore", "Timed out restoring charging after ${intent.action}", error)
            } catch (error: ForegroundServiceStartNotAllowedException) {
                Log.e("ChargingRestore", "System blocked charging service restart after ${intent.action}", error)
            } catch (error: SecurityException) {
                Log.e("ChargingRestore", "Permission denied restoring charging after ${intent.action}", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
