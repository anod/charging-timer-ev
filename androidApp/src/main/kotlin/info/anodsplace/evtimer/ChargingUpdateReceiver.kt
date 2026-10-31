package info.anodsplace.evtimer

import android.app.ForegroundServiceStartNotAllowedException
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

class ChargingUpdateReceiver : BroadcastReceiver(), KoinComponent {
    private val chargingService: ChargingService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val status = withTimeout(8_000L) { chargingService.status.first { !it.isLoading } }
                if (status.isRunning) {
                    context.startForegroundService(Intent(context, ChargingForegroundService::class.java))
                }
            } catch (error: TimeoutCancellationException) {
                Log.e("ChargingUpdate", "Timed out loading the session after update", error)
            } catch (error: ForegroundServiceStartNotAllowedException) {
                Log.e("ChargingUpdate", "System blocked charging service restart after update", error)
            } catch (error: SecurityException) {
                Log.e("ChargingUpdate", "Permission denied restarting charging service after update", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
