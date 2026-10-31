package info.anodsplace.evtimer.ui

import info.anodsplace.evtimer.data.ChargingRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpandedCardsTest {
    @Test
    fun customSelectionKeepsCurrentDefaults() {
        assertEquals(
            ExpandedCards(battery = false, max = false, start = true, power = true),
            expandedCards(ChargingRepositoryImpl.CUSTOM_ID)
        )
    }

    @Test
    fun namedPresetCollapsesBatteryMaxAndPower() {
        assertEquals(
            ExpandedCards(battery = false, max = false, start = true, power = false),
            expandedCards("home")
        )
    }
}
