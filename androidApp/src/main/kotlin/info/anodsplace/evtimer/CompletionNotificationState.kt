package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.reachedTarget

internal enum class CompletionNotificationAction { Keep, Post, Cancel }

internal class CompletionNotificationState {
    private var initialized = false
    private var initialSessionId: Long? = null
    private var notifiedSessionId: Long? = null

    fun update(isRunning: Boolean, latest: ChargingHistoryEntry?): CompletionNotificationAction {
        if (!initialized) {
            initialSessionId = latest?.id
            initialized = true
        }
        if (isRunning) return CompletionNotificationAction.Keep
        if (latest == null || latest.reviewed || !latest.reachedTarget) {
            return CompletionNotificationAction.Cancel
        }
        // Cold stopped history must not re-alert after the user taps an auto-cancel notification.
        if (latest.id == initialSessionId || latest.id == notifiedSessionId) {
            return CompletionNotificationAction.Keep
        }
        notifiedSessionId = latest.id
        return CompletionNotificationAction.Post
    }
}
