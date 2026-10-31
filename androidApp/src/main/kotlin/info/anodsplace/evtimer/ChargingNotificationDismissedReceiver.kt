package info.anodsplace.evtimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ChargingNotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(ChargingNotifier.EXTRA_SESSION_ID, 0)
        if (sessionId == 0L) return
        val app = context.applicationContext as MainApplication
        app.chargingSessionController.dismiss(sessionId)
    }
}
