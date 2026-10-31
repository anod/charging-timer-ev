package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ChargingSettingsTest {

    @Test
    fun addingNewPowerAddsAndSelectsItWithoutChangingOtherSettings() {
        val settings = ChargingSettings(
            batteryCapacity = 83f,
            startPercent = 60f,
            maxPercent = 100f,
            availablePowers = mutableListOf(3.6f, 7f, 11f)
        )

        val updated = settings.withAddedPower(7.2f)

        assertEquals(
            settings.copy(
                chargingPower = 7.2f,
                availablePowers = listOf(3.6f, 7f, 7.2f, 11f)
            ),
            updated
        )
        assertEquals(7f, settings.chargingPower)
        assertEquals(listOf(3.6f, 7f, 11f), settings.availablePowers)
    }

    @Test
    fun addingExistingPowerSelectsItWithoutDuplicates() {
        val settings = ChargingSettings(availablePowers = listOf(3.6f, 7f, 11f))

        val updated = settings.withAddedPower(11f)

        assertEquals(11f, updated.chargingPower)
        assertEquals(settings.availablePowers, updated.availablePowers)
    }

    @Test
    fun addingNewPowerSortsAndDeduplicatesTheList() {
        val settings = ChargingSettings(availablePowers = listOf(22f, 7f, 11f, 7f, 3.6f))

        val updated = settings.withAddedPower(7.2f)

        assertEquals(7.2f, updated.chargingPower)
        assertEquals(listOf(3.6f, 7f, 7.2f, 11f, 22f), updated.availablePowers)
    }

    @Test
    fun addingExistingPowerSortsAndDeduplicatesTheList() {
        val settings = ChargingSettings(availablePowers = listOf(22f, 11f, 3.6f, 7f, 11f))

        val updated = settings.withAddedPower(11f)

        assertEquals(11f, updated.chargingPower)
        assertEquals(listOf(3.6f, 7f, 11f, 22f), updated.availablePowers)
    }
}
