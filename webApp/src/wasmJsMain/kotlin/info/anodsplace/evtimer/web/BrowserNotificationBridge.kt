@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package info.anodsplace.evtimer.web

import info.anodsplace.evtimer.data.*
import kotlinx.coroutines.await
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

@JsFun("() => globalThis.evTimerNotifications.permission()")
private external fun notificationPermission(): String

@JsFun("() => globalThis.evTimerNotifications.requestPermission()")
private external fun requestNotificationPermission(): Promise<JsString>

@JsFun("() => document.visibilityState === 'visible'")
private external fun pageVisible(): Boolean

@JsFun("(title, body, tag) => globalThis.evTimerNotifications.show(title, body, tag)")
private external fun showNotification(title: JsString, body: JsString, tag: JsString)

internal class BrowserNotificationBridge(
    private val completeTitle: String,
    private val milestoneTitle: String,
    private val body: String
) : ForegroundNotifications {
    override fun permission(): BrowserPermission = permission(notificationPermission())
    override suspend fun requestPermission(): BrowserPermission =
        permission(requestNotificationPermission().await().toString())
    override fun isForeground() = pageVisible()
    override fun show(alert: BrowserAlert) {
        showNotification(
            (if (alert == BrowserAlert.COMPLETE) completeTitle else milestoneTitle).toJsString(),
            body.toJsString(), alert.name.toJsString()
        )
    }

    private fun permission(value: String): BrowserPermission = when (value) {
        "granted" -> BrowserPermission.GRANTED
        "denied" -> BrowserPermission.DENIED
        "default" -> BrowserPermission.DEFAULT
        else -> BrowserPermission.UNSUPPORTED
    }
}
