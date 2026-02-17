package info.anodsplace.evtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import info.anodsplace.evtimer.data.ChargingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val CHANNEL_ID = "charging_progress"
private const val NOTIFICATION_ID = 2101
private const val TAG = "ChargingFgSvc"
private const val ACTION_STOP_CHARGING = "ACTION_STOP_CHARGING"
private const val STOP_CHARGING_REQUEST_CODE = 1

class ChargingForegroundService : Service(), KoinComponent {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val chargingService: ChargingService by inject()
    private var collectorJob: Job? = null
    private var lastPercentInt: Int? = null
    private var startedForeground = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop action from notification
        if (intent?.action == ACTION_STOP_CHARGING) {
            scope.launch {
                // Calling stop() will update the status flow, which will trigger
                // the collector below to call stopForegroundSafely() and stopSelf()
                chargingService.stop()
            }
            return START_STICKY
        }
        
        if (!startedForeground) {
            val initial = chargingService.status.value
            if (initial.isRunning) {
                val initialPercent = initial.currentPercent.toInt().coerceIn(0, 100)
                startInForeground(buildNotification(initial, initialPercent))
                lastPercentInt = initialPercent
            }
        }
        if (collectorJob == null) {
            collectorJob = scope.launch {
                chargingService.status.collectLatest { status ->
                    if (status.isRunning) {
                        val percentInt = status.currentPercent.toInt().coerceIn(0, 100)
                        val shouldUpdate = !startedForeground || lastPercentInt != percentInt
                        if (!startedForeground) {
                            startInForeground(buildNotification(status, percentInt))
                        } else if (shouldUpdate) {
                            postOrLog(percentInt) { buildNotification(status, percentInt) }
                        }
                        lastPercentInt = percentInt
                    } else {
                        stopForegroundSafely()
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun postOrLog(percentInt: Int, notificationBuilder: () -> Notification) {
        val mgr = NotificationManagerCompat.from(this)
        if (!mgr.areNotificationsEnabled()) return
        if (
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Skipping notify, POST_NOTIFICATIONS not granted")
            return
        }
        try {
            mgr.notify(NOTIFICATION_ID, notificationBuilder())
        } catch (se: SecurityException) {
            Log.w(TAG, "notify SecurityException for %=$percentInt: ${se.message}")
        } catch (t: Throwable) {
            Log.w(TAG, "notify failed for %=$percentInt: ${t.message}")
        }
    }

    private fun startInForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // Use special use type as requested
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            startedForeground = true
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed: ${t.message}")
            postOrLog(lastPercentInt ?: -1) { notification }
        }
    }

    private fun stopForegroundSafely() {
        if (startedForeground) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Throwable) {
            }
            startedForeground = false
        } else {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        }
    }

    override fun onDestroy() {
        collectorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Charging Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows real-time EV charging progress"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopChargingIntent(): PendingIntent {
        val intent = Intent(this, ChargingForegroundService::class.java).apply {
            action = ACTION_STOP_CHARGING
        }
        return PendingIntent.getService(
            this,
            STOP_CHARGING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(status: info.anodsplace.evtimer.data.ChargingStatus, percentInt: Int): Notification {
        val calc = status.calculation
        val speed = String.format(Locale.US, "%.1f kW", calc.chargingSpeed)
        val remainingMin = calc.timeRemainingMinutes
        val remainingText = if (remainingMin > 0) {
            val h = remainingMin / 60
            val m = remainingMin % 60
            if (h > 0) "${h}h ${m}m left" else "${m}m left"
        } else if (percentInt >= 100) "Completed" else "Updating..."
        val eta = if (status.estimatedEndTime > 0) {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.estimatedEndTime))
        } else null
        val line = buildString {
            append(percentInt).append('%')
            append(" • ").append(speed)
            append(" • ").append(remainingText)
            if (eta != null) append(" • ETA ").append(eta)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EV Charging")
            .setContentText(line)
            .setSmallIcon(applicationInfo.icon)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentIntent())
            .setProgress(100, percentInt, false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopChargingIntent())
            .build()
    }
}
