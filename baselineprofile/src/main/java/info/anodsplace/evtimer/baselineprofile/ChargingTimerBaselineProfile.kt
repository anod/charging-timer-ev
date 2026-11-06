package info.anodsplace.evtimer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChargingTimerBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndSettingsNavigation() = baselineProfileRule.collect(
        packageName = "info.anodsplace.evtimer"
    ) {
        pressHome()
        startActivityAndWait()

        check(device.wait(Until.hasObject(By.desc("Settings")), 5_000)) {
            "The charging configuration screen did not expose its Settings action."
        }
        device.findObject(By.desc("Settings")).click()
        check(device.wait(Until.hasObject(By.text("Settings")), 5_000)) {
            "The Settings screen did not open."
        }
        device.pressBack()
    }
}
