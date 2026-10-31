package info.anodsplace.evtimer.ui

import info.anodsplace.evtimer.data.ChargingRepositoryImpl

internal data class ExpandedCards(
    val battery: Boolean,
    val max: Boolean,
    val start: Boolean,
    val power: Boolean
)

internal fun expandedCards(selection: String): ExpandedCards {
    val isCustom = selection == ChargingRepositoryImpl.CUSTOM_ID
    return ExpandedCards(
        battery = false,
        max = false,
        start = true,
        power = isCustom
    )
}
