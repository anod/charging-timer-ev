package info.anodsplace.evtimer.ui

import androidx.compose.runtime.saveable.listSaver
import info.anodsplace.evtimer.data.CUSTOM_PRESET_ID

internal data class ExpandedCards(
    val battery: Boolean,
    val max: Boolean,
    val start: Boolean,
    val power: Boolean,
    val taper: Boolean = false
)

internal data class PresetCardExpansion(
    val presetId: String,
    val cards: ExpandedCards
)

internal val presetCardExpansionSaver = listSaver<PresetCardExpansion, Any>(
    save = {
        listOf(
            it.presetId,
            it.cards.battery,
            it.cards.max,
            it.cards.start,
            it.cards.power,
            it.cards.taper
        )
    },
    restore = { saved ->
        val presetId = saved.getOrNull(0) as? String
        val battery = saved.getOrNull(1) as? Boolean
        val max = saved.getOrNull(2) as? Boolean
        val start = saved.getOrNull(3) as? Boolean
        val power = saved.getOrNull(4) as? Boolean
        val taper = saved.getOrNull(5) as? Boolean ?: false
        if (presetId == null || battery == null || max == null || start == null || power == null) null
        else PresetCardExpansion(presetId, ExpandedCards(battery, max, start, power, taper))
    }
)

internal fun expandedCards(selection: String): ExpandedCards {
    val isCustom = selection == CUSTOM_PRESET_ID
    return ExpandedCards(
        battery = false,
        max = false,
        start = true,
        power = isCustom,
        taper = false
    )
}
