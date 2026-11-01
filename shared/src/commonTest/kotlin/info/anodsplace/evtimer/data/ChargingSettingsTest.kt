package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChargingSettingsTest {
    @Test
    fun removingSelectedPowerSelectsFirstRemainingOption() {
        val settings = ChargingSettings.DEFAULT
        val updated = settings.withRemovedPower(11f)
        assertEquals(settings.copy(chargingPower = 3.6f, availablePowers = listOf(3.6f, 7f, 22f)), updated)
        assertEquals(listOf(3.6f, 7f, 11f, 22f), settings.availablePowers)
    }

    @Test
    fun removingOtherPowerKeepsSelectionAndRejectsMissingOrLastOption() {
        val settings = ChargingSettings.DEFAULT
        assertEquals(settings.copy(availablePowers = listOf(7f, 11f, 22f)), settings.withRemovedPower(3.6f))
        assertFailsWith<IllegalArgumentException> { settings.withRemovedPower(50f) }
        assertFailsWith<IllegalArgumentException> {
            settings.copy(availablePowers = listOf(11f)).withRemovedPower(11f)
        }
    }

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
        assertEquals(11f, settings.chargingPower)
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
