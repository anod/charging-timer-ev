package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewStateCompatibilityTest {
    @Test
    fun positionalConstructorsRetainTheirOriginalParameterOrder() {
        val settings = ChargingSettings()
        val presets = listOf(ChargingPreset("custom", "Default", settings))
        val configuration = ConfigurationViewState(false, settings, presets, "custom")
        val running = RunningViewState(false, 123L, 456L)
        val status = ChargingStatus(false, true, 123L)

        assertEquals(settings, configuration.settings)
        assertEquals(presets, configuration.presets)
        assertEquals("custom", configuration.selectedPresetId)
        assertEquals(123L, running.startTime)
        assertEquals(456L, running.estimatedEndTime)
        assertTrue(status.isRunning)
        assertEquals(123L, status.startTime)
        assertFalse(configuration.loadFailed)
        assertFalse(running.loadFailed)
        assertFalse(status.loadFailed)
    }
}
