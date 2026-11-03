package info.anodsplace.evtimer.data

import kotlin.time.Clock

enum class BrowserPermission { DEFAULT, GRANTED, DENIED, UNSUPPORTED }
enum class BrowserAlert { MILESTONE, COMPLETE }

interface ForegroundNotifications {
    fun permission(): BrowserPermission
    suspend fun requestPermission(): BrowserPermission
    fun isForeground(): Boolean
    fun show(alert: BrowserAlert)
}

class BrowserNotificationException(message: String) : IllegalStateException(message)

class BrowserNotifications(
    private val storage: BrowserStorage,
    private val browser: ForegroundNotifications,
    private val clock: Clock = Clock.System
) {
    /** Call only from the user's permission button, never startup or a timer. */
    suspend fun enable() {
        if (browser.requestPermission() != BrowserPermission.GRANTED) {
            storage.update { it.copy(notificationsEnabled = false) }
            throw BrowserNotificationException("Notification permission was denied or is unavailable")
        }
        storage.update { it.copy(notificationsEnabled = true) }
    }

    suspend fun disable() {
        storage.update { it.copy(notificationsEnabled = false) }
    }

    suspend fun deliver() {
        if (!browser.isForeground()) return
        var alert: BrowserAlert? = null
        val before = storage.load()
        if (!before.notificationsEnabled) return
        if (browser.permission() != BrowserPermission.GRANTED) {
            throw BrowserNotificationException("Notification permission is no longer available")
        }
        val now = clock.now().toEpochMilliseconds()
        fun candidate(state: BrowserState): BrowserAlert? {
            val session = state.activeSession?.takeIf { it.id == state.notificationSessionId }
                ?: state.backup.sessions.firstOrNull { it.id == state.notificationSessionId }
                ?: return null
            val snapshot = session.snapshot()
            return when {
                now >= snapshot.targetTime() &&
                    (session.endedAt == null || session.endedAt >= snapshot.targetTime()) ->
                    BrowserAlert.COMPLETE.takeUnless { state.completionNotificationAttempted }
                session.endedAt == null && state.backup.preferences.notifyAt80 &&
                    ChargingCalculator.estimateMilestone(snapshot, now)?.reached == true ->
                    BrowserAlert.MILESTONE.takeUnless { state.milestoneNotificationAttempted }
                else -> null
            }
        }
        if (candidate(before) == null) return
        // Claim durably before invoking the browser: at most one attempt across tabs/reloads.
        storage.update { state ->
            if (!browser.isForeground()) return@update state
            alert = candidate(state)
            when (alert) {
                BrowserAlert.COMPLETE -> state.copy(completionNotificationAttempted = true)
                BrowserAlert.MILESTONE -> state.copy(milestoneNotificationAttempted = true)
                null -> state
            }
        }
        alert?.let {
            if (!browser.isForeground()) {
                throw BrowserNotificationException("Page became hidden before notification delivery")
            }
            browser.show(it)
        }
    }
}
