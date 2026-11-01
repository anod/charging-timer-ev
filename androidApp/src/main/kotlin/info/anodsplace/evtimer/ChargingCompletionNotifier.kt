package info.anodsplace.evtimer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ChargingCompletionNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun build(at80: Boolean): Notification {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, context.getString(R.string.charging_complete_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.charging_complete_channel_description)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 500L, 250L, 500L)
                setBypassDnd(false)
            }
        )
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_charging)
            .setContentTitle(context.getString(if (at80) R.string.charging_80_title else R.string.charging_complete_title))
            .setContentText(context.getString(if (at80) R.string.charging_80_message else R.string.charging_complete_message))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
    }

    fun post(at80: Boolean = false) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            Log.w("ChargingComplete", "Completion notification disabled or POST_NOTIFICATIONS denied")
            return
        }
        try {
            manager.notify(if (at80) MILESTONE_NOTIFICATION_ID else NOTIFICATION_ID, build(at80))
        } catch (error: SecurityException) {
            Log.w("ChargingComplete", "Completion notification permission changed", error)
        }
    }

    fun cancel(at80: Boolean = false) = manager.cancel(if (at80) MILESTONE_NOTIFICATION_ID else NOTIFICATION_ID)

    companion object {
        const val CHANNEL_ID = "charging_complete"
        const val NOTIFICATION_ID = 2102
        const val MILESTONE_NOTIFICATION_ID = 2103
        const val EXTRA_AT_80 = "at_80"
    }
}
