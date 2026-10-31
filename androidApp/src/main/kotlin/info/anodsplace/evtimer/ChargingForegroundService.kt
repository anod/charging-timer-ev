package info.anodsplace.evtimer

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ClockTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ChargingForegroundService : Service(), KoinComponent {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val chargingService: ChargingService by inject()
    private val clockTimeFormatter: ClockTimeFormatter by inject()
    private lateinit var notifier: ChargingNotifier
    private var collectorJob: Job? = null
    private var lastContent: ChargingNotificationContent? = null

    override fun onCreate() {
        super.onCreate()
        notifier = ChargingNotifier(this)
        notifier.ensureChannel()
        // Fulfil the foreground deadline even while DataStore is still loading.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                ChargingNotifier.NOTIFICATION_ID, notifier.placeholder(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ChargingNotifier.NOTIFICATION_ID, notifier.placeholder())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ChargingNotifier.ACTION_STOP_CHARGING) {
            val sessionId = intent.getLongExtra(ChargingNotifier.EXTRA_SESSION_ID, 0)
            scope.launch {
                val status = chargingService.status.first { !it.isLoading }
                if (status.isRunning && status.startTime == sessionId) chargingService.stop()
            }
        }
        if (collectorJob == null) {
            collectorJob = scope.launch {
                chargingService.status.collectLatest { status ->
                    if (status.isLoading) return@collectLatest
                    val controller = (application as MainApplication).chargingSessionController
                    if (!status.isRunning || controller.isDismissed(status.startTime)) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val content = ChargingNotificationContent.from(
                            status,
                            clockTimeFormatter,
                            chargingNotificationTextFormatter(this@ChargingForegroundService)
                        )
                        if (content != lastContent) {
                            notifier.post(content)
                            lastContent = content
                        }
                    }
                }
            }
        } else {
            // Recheck promotion and notification permission after returning from Settings.
            lastContent?.let(notifier::post)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
