package info.anodsplace.evtimer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri

class ChargingNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, context.getString(R.string.charging_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.charging_channel_description)
                setShowBadge(false)
            }
        )
    }

    fun placeholder(): Notification = builder(0)
        .setContentText(context.getString(R.string.charging_loading))
        .setProgress(100, 0, true)
        .build()

    fun build(content: ChargingNotificationContent): Notification {
        val builder = builder(content.sessionId)
            .setContentText(content.text)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.charging_stop), stopIntent(content.sessionId)
            )
        if (Build.VERSION.SDK_INT >= 36) {
            val colors = intArrayOf(0xff388e3c.toInt(), 0xff1976d2.toInt())
            builder
                .setRequestPromotedOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShortCriticalText(content.criticalText)
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgress(content.percent)
                        .setProgressSegments(content.segmentLengths.mapIndexed { index, length ->
                            NotificationCompat.ProgressStyle.Segment(length)
                                .setColor(colors[index % colors.size])
                        })
                        .setProgressPoints(content.speedChangePoints.map {
                            NotificationCompat.ProgressStyle.Point(it)
                        })
                        .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_charging))
                )
        } else {
            builder.setProgress(100, content.percent, false)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
        }
        return builder.build()
    }

    fun post(content: ChargingNotificationContent) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            Log.w(TAG, "Charging notification disabled or POST_NOTIFICATIONS denied")
            return
        }
        try {
            manager.notify(NOTIFICATION_ID, build(content))
        } catch (e: SecurityException) {
            Log.w(TAG, "Charging notification permission changed", e)
        }
    }

    fun canPromote(): Boolean = Build.VERSION.SDK_INT >= 36 && manager.canPostPromotedNotifications()

    private fun builder(sessionId: Long): NotificationCompat.Builder {
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissed = PendingIntent.getBroadcast(
            context, 0, Intent(context, ChargingNotificationDismissedReceiver::class.java).apply {
                data = "evtimer://dismiss/$sessionId".toUri()
                putExtra(EXTRA_SESSION_ID, sessionId)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.charging_title))
            .setSmallIcon(R.drawable.ic_charging)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setDeleteIntent(dismissed)
    }

    private fun stopIntent(sessionId: Long): PendingIntent = PendingIntent.getService(
        context, 1, Intent(context, ChargingForegroundService::class.java).apply {
            action = ACTION_STOP_CHARGING
            data = "evtimer://stop/$sessionId".toUri()
            putExtra(EXTRA_SESSION_ID, sessionId)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val NOTIFICATION_ID = 2101
        const val EXTRA_SESSION_ID = "session_id"
        const val ACTION_STOP_CHARGING = "info.anodsplace.evtimer.ACTION_STOP_CHARGING"
        private const val CHANNEL_ID = "charging_progress"
        private const val TAG = "ChargingNotifier"
    }
}
