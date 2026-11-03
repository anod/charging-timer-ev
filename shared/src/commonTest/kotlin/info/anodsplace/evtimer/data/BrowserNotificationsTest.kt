package info.anodsplace.evtimer.data

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BrowserNotificationsTest {
    private class Browser : ForegroundNotifications {
        var permissionValue = BrowserPermission.DEFAULT
        var foreground = true
        var requests = 0
        var failShow = false
        val alerts = mutableListOf<BrowserAlert>()
        override fun permission() = permissionValue
        override suspend fun requestPermission(): BrowserPermission {
            requests++
            return permissionValue
        }
        override fun isForeground() = foreground
        override fun show(alert: BrowserAlert) {
            if (failShow) throw BrowserNotificationException("Notification constructor failed")
            alerts += alert
        }
    }

    private val clock = BrowserTestClock()
    private val store = BrowserTestStore()
    private val storage = BrowserStorage(store, clock)
    private val runtime = BrowserTimerRuntime(storage, clock)
    private val browser = Browser()
    private fun notifications() = BrowserNotifications(storage, browser, clock)

    private suspend fun start() {
        runtime.reconcile()
        runtime.configure(ConfigurationViewEvent.UpdateBatteryCapacity(100f))
        runtime.configure(ConfigurationViewEvent.UpdateChargingPower(10f))
        runtime.configure(ConfigurationViewEvent.UpdateStartPercent(70f))
        runtime.configure(ConfigurationViewEvent.UpdateMaxPercent(90f))
        runtime.start()
    }

    @Test
    fun startupNeverRequestsPermissionAndDeniedOrUnsupportedIsExplicit() = runTest {
        start()
        notifications().deliver()
        assertEquals(0, browser.requests)
        for (permission in listOf(BrowserPermission.DENIED, BrowserPermission.UNSUPPORTED, BrowserPermission.DEFAULT)) {
            browser.permissionValue = permission
            assertFailsWith<BrowserNotificationException> { notifications().enable() }
            assertFalse(storage.load().notificationsEnabled)
        }
        assertEquals(3, browser.requests)
        assertTrue(browser.alerts.isEmpty())
    }

    @Test
    fun foregroundMilestoneAndCompletionAreClaimedOnceAcrossReloads() = runTest {
        start()
        browser.permissionValue = BrowserPermission.GRANTED
        notifications().enable()
        clock.epoch += 3_600_000
        repeat(2) { notifications().deliver() }
        assertEquals(listOf(BrowserAlert.MILESTONE), browser.alerts)
        clock.epoch += 3_600_000
        runtime.reconcile()
        repeat(2) { notifications().deliver() }
        assertEquals(listOf(BrowserAlert.MILESTONE, BrowserAlert.COMPLETE), browser.alerts)
    }

    @Test
    fun hiddenPageDoesNotDeliverAndResumeSendsOnlyCompletionNotStaleMilestone() = runTest {
        start()
        browser.permissionValue = BrowserPermission.GRANTED
        notifications().enable()
        browser.foreground = false
        clock.epoch += 10 * 3_600_000
        runtime.reconcile()
        notifications().deliver()
        assertTrue(browser.alerts.isEmpty())
        assertFalse(storage.load().completionNotificationAttempted)
        browser.foreground = true
        notifications().deliver()
        assertEquals(listOf(BrowserAlert.COMPLETE), browser.alerts)
    }

    @Test
    fun deliveryAndRevokedPermissionFailuresAreNotSuccessOrRepeatedAttempts() = runTest {
        start()
        browser.permissionValue = BrowserPermission.GRANTED
        notifications().enable()
        clock.epoch += 3_600_000
        browser.permissionValue = BrowserPermission.DENIED
        assertFailsWith<BrowserNotificationException> { notifications().deliver() }
        assertFalse(storage.load().milestoneNotificationAttempted)
        browser.permissionValue = BrowserPermission.GRANTED
        browser.failShow = true
        assertFailsWith<BrowserNotificationException> { notifications().deliver() }
        assertTrue(storage.load().milestoneNotificationAttempted)
        browser.failShow = false
        notifications().deliver()
        assertTrue(browser.alerts.isEmpty(), "A failed browser attempt must not spam alerts on every refresh")
    }

    @Test
    fun storageFailureCannotSendUnclaimedNotificationAndManualStopCannotAlertCompletion() = runTest {
        start()
        browser.permissionValue = BrowserPermission.GRANTED
        notifications().enable()
        clock.epoch += 3_600_000
        store.failWrite = true
        assertFailsWith<IllegalStateException> { notifications().deliver() }
        assertTrue(browser.alerts.isEmpty())
        store.failWrite = false
        runtime.stop()
        clock.epoch += 10 * 3_600_000
        notifications().deliver()
        assertTrue(browser.alerts.isEmpty())
    }
}
