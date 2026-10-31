package info.anodsplace.evtimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ChargingCompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ChargingCompletionNotifier(context).post(intent.getBooleanExtra(ChargingCompletionNotifier.EXTRA_AT_80, false))
    }
}
