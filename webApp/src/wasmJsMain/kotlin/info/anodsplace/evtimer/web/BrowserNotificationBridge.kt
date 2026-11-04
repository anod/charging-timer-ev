@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package info.anodsplace.evtimer.web

import info.anodsplace.evtimer.data.*
import kotlinx.coroutines.await
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

@JsFun("""() => {
    try { return globalThis.evTimerNotifications.permission(); }
    catch (error) { return "error:" + String(error?.message || error); }
}""")
private external fun notificationPermission(): String

@JsFun("""() => {
    try {
        return globalThis.evTimerNotifications.requestPermission()
            .catch(error => "error:" + String(error?.message || error));
    } catch (error) {
        return Promise.resolve("error:" + String(error?.message || error));
    }
}""")
private external fun requestNotificationPermission(): Promise<JsString>

@JsFun("() => document.visibilityState === 'visible'")
private external fun pageVisible(): Boolean

@JsFun("""(title, body, tag) => {
    try {
        globalThis.evTimerNotifications.show(title, body, tag);
        return null;
    } catch (error) {
        return String(error?.message || error);
    }
}""")
private external fun showNotification(title: JsString, body: JsString, tag: JsString): JsString?

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
        val failure = showNotification(
            (if (alert == BrowserAlert.COMPLETE) completeTitle else milestoneTitle).toJsString(),
            body.toJsString(), alert.name.toJsString()
        )
        if (failure != null) throw BrowserNotificationException(failure.toString())
    }

    private fun permission(value: String): BrowserPermission {
        if (value.startsWith("error:")) throw BrowserNotificationException(value.removePrefix("error:"))
        return when (value) {
            "granted" -> BrowserPermission.GRANTED
            "denied" -> BrowserPermission.DENIED
            "default" -> BrowserPermission.DEFAULT
            else -> BrowserPermission.UNSUPPORTED
        }
    }
}
