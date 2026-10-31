package info.anodsplace.evtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import info.anodsplace.evtimer.data.ChargingCompletionPlan
import info.anodsplace.evtimer.data.ChargingCompletionPlanner
import info.anodsplace.evtimer.data.ChargingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Independent of progress-notification dismissal; no vehicle is controlled. */
class ChargingCompletionController(
    private val context: Context,
    private val chargingService: ChargingService,
    private val scope: CoroutineScope
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifier = ChargingCompletionNotifier(context)
    // Device-local delivery/acknowledgment state, explicitly excluded from backup.
    private val preferences = context.getSharedPreferences("charging_completion", Context.MODE_PRIVATE)
    private var scheduled: ChargingCompletionPlan? = null
    private val mutablePendingSession = MutableStateFlow<Long?>(null)
    val pendingSession = mutablePendingSession.asStateFlow()

    init {
        scope.launch {
            chargingService.status.collect { status ->
                if (!status.isLoading) sync()
            }
        }
    }

    fun sync(force: Boolean = false) {
        val status = chargingService.status.value
        if (status.isLoading) return
        val alerted = if (preferences.contains(KEY_SESSION)) preferences.getLong(KEY_SESSION, 0L) else null
        if (!status.isRunning || (alerted != null && alerted != status.startTime)) {
            notifier.cancel()
            mutablePendingSession.value = null
            if (alerted != null) saveState(null)
        } else if (alerted == status.startTime && !preferences.getBoolean(KEY_DISMISSED, false)) {
            mutablePendingSession.value = alerted
        }
        val plan = ChargingCompletionPlanner.plan(status, alerted)
        if (plan == null) {
            alarmManager.cancel(alarmIntent())
            scheduled = null
        } else if (force || plan != scheduled) {
            schedule(plan)
        }
    }

    fun onAlarm(sessionId: Long, targetTime: Long) {
        chargingService.refresh()
        val status = chargingService.status.value
        val alerted = if (preferences.contains(KEY_SESSION)) preferences.getLong(KEY_SESSION, 0L) else null
        val plan = ChargingCompletionPlanner.plan(status, alerted)
        if (plan == null || plan.sessionId != sessionId || plan.targetTime != targetTime ||
            System.currentTimeMillis() < plan.targetTime
        ) {
            sync(force = true)
            return
        }
        saveState(sessionId)
        scheduled = null
        alarmManager.cancel(alarmIntent())
        notifier.post(sessionId)
        mutablePendingSession.value = sessionId
    }

    fun dismiss(sessionId: Long, stopCharging: Boolean = false) {
        if (!preferences.contains(KEY_SESSION) || preferences.getLong(KEY_SESSION, 0L) != sessionId) return
        // Cancel first: removing FLAG_INSISTENT stops both system-managed effects.
        notifier.cancel()
        mutablePendingSession.value = null
        saveState(sessionId, dismissed = true)
        if (stopCharging) scope.launch {
            val status = chargingService.status.value
            if (status.isRunning && status.startTime == sessionId) chargingService.stop()
        }
    }

    private fun schedule(plan: ChargingCompletionPlan) {
        val operation = alarmIntent(plan)
        val triggerTime = plan.triggerTime(System.currentTimeMillis())
        if (alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, operation)
            } catch (error: SecurityException) {
                Log.w("ChargingComplete", "Exact-alarm access changed; scheduling an inexact idle alarm", error)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, operation)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, operation)
        }
        scheduled = plan
    }

    private fun alarmIntent(plan: ChargingCompletionPlan? = null): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ChargingCompletionReceiver::class.java).apply {
            action = ACTION_COMPLETE
            data = "evtimer://completion/alarm".toUri()
            if (plan != null) {
                putExtra(ChargingCompletionNotifier.EXTRA_SESSION_ID, plan.sessionId)
                putExtra(EXTRA_TARGET_TIME, plan.targetTime)
            }
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun saveState(sessionId: Long?, dismissed: Boolean = false) {
        val editor = preferences.edit()
        if (sessionId == null) editor.clear() else {
            editor.putLong(KEY_SESSION, sessionId).putBoolean(KEY_DISMISSED, dismissed)
        }
        if (!editor.commit()) Log.e("ChargingComplete", "Could not persist completion delivery state")
    }

    companion object {
        const val ACTION_COMPLETE = "info.anodsplace.evtimer.CHARGING_COMPLETE"
        const val EXTRA_TARGET_TIME = "target_time"
        private const val KEY_SESSION = "session_id"
        private const val KEY_DISMISSED = "dismissed"
    }
}
