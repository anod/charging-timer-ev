package info.anodsplace.evtimer

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import info.anodsplace.evtimer.data.ChargingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Owns Android service lifetime independently of activity composition. */
class ChargingSessionController(
    private val context: Context,
    private val chargingService: ChargingService,
    scope: CoroutineScope
) {
    private val preferences = context.getSharedPreferences("charging_notification", Context.MODE_PRIVATE)
    private var activeSessionId: Long? = null

    init {
        scope.launch {
            chargingService.status.collectLatest { status ->
                if (!status.isLoading) sync()
            }
        }
    }

    fun isDismissed(sessionId: Long): Boolean =
        preferences.contains("dismissed_session") &&
            preferences.getLong("dismissed_session", 0) == sessionId

    fun dismiss(sessionId: Long) {
        preferences.edit { putLong("dismissed_session", sessionId) }
        if (chargingService.status.value.startTime == sessionId) {
            context.stopService(Intent(context, ChargingForegroundService::class.java))
            activeSessionId = null
        }
    }

    fun onActivityResumed() = sync(refresh = true)

    private fun sync(refresh: Boolean = false) {
        val status = chargingService.status.value
        if (status.isLoading) return
        if (!status.isRunning) {
            context.stopService(Intent(context, ChargingForegroundService::class.java))
            activeSessionId = null
            return
        }
        if (isDismissed(status.startTime)) return
        if (activeSessionId == status.startTime && !refresh) return
        try {
            context.startForegroundService(Intent(context, ChargingForegroundService::class.java))
            activeSessionId = status.startTime
        } catch (e: IllegalStateException) {
            // Restored sessions can load while the process is backgrounded; retry on resume.
            if (e is ForegroundServiceStartNotAllowedException) {
                Log.w("ChargingController", "Cannot resume charging foreground service until app is visible", e)
            } else {
                throw e
            }
        }
    }
}
