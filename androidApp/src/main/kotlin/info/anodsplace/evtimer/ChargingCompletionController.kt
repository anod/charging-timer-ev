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
import kotlinx.coroutines.launch

/** Independent of progress-notification dismissal; no vehicle is controlled. */
class ChargingCompletionController(
    private val context: Context,
    private val chargingService: ChargingService,
    private val scope: CoroutineScope
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifier = ChargingCompletionNotifier(context)
    // Device-local delivery marker, excluded by the backup allow-list.
    private val preferences = context.getSharedPreferences("charging_completion", Context.MODE_PRIVATE)
    private var scheduled: ChargingCompletionPlan? = null
    private var isAppVisible = false

    init {
        scope.launch {
            chargingService.status.collect { status ->
                if (!status.isLoading) sync()
            }
        }
    }

    fun onActivityResumed() {
        isAppVisible = true
        notifier.cancel()
        sync(force = true)
    }

    fun onActivityPaused() {
        isAppVisible = false
    }

    fun sync(force: Boolean = false) {
        val status = chargingService.status.value
        if (status.isLoading) return
        val alerted = if (preferences.contains(KEY_SESSION)) preferences.getLong(KEY_SESSION, 0L) else null
        if (!status.isRunning || (alerted != null && alerted != status.startTime)) {
            notifier.cancel()
            if (alerted != null) saveState(null)
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
        if (!isAppVisible) notifier.post(sessionId)
    }

    fun dismiss(sessionId: Long) {
        if (!preferences.contains(KEY_SESSION) || preferences.getLong(KEY_SESSION, 0L) != sessionId) return
        // Cancel first: removing FLAG_INSISTENT stops both system-managed effects.
        notifier.cancel()
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

    private fun saveState(sessionId: Long?) {
        val editor = preferences.edit()
        if (sessionId == null) editor.clear() else {
            editor.putLong(KEY_SESSION, sessionId)
        }
        if (!editor.commit()) Log.e("ChargingComplete", "Could not persist completion delivery state")
    }

    companion object {
        const val ACTION_COMPLETE = "info.anodsplace.evtimer.CHARGING_COMPLETE"
        const val EXTRA_TARGET_TIME = "target_time"
        private const val KEY_SESSION = "session_id"
    }
}
