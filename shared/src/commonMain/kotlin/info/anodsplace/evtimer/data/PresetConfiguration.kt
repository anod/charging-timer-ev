package info.anodsplace.evtimer.data

data class PresetConfiguration(
    val presets: List<ChargingPreset>,
    val selectedId: String,
    val settings: ChargingSettings
) {
    val selected: ChargingPreset get() = presets.first { it.id == selectedId }
    val isEdited: Boolean get() = selectedId != ChargingRepositoryImpl.CUSTOM_ID &&
        !settings.matchesPreset(selected.settings)

    fun reduce(event: PresetEvent): PresetConfiguration = when (event) {
        is PresetEvent.Select -> {
            val preset = presets.firstOrNull { it.id == event.id }
                ?: presets.first { it.id == ChargingRepositoryImpl.CUSTOM_ID }
            copy(selectedId = preset.id, settings = preset.settings.copy(startPercent = settings.startPercent))
        }
        is PresetEvent.Edit -> copy(
            settings = event.settings,
            presets = if (selectedId == ChargingRepositoryImpl.CUSTOM_ID) {
                presets.map { if (it.id == selectedId) it.copy(settings = event.settings) else it }
            } else presets
        )
        PresetEvent.Update -> copy(presets = presets.map {
            if (it.id == selectedId) it.copy(settings = settings) else it
        })
        is PresetEvent.SaveNew -> {
            require(event.id.isNotBlank() && presets.none { it.id == event.id })
            require(event.name.isNotBlank())
            copy(presets = presets + ChargingPreset(event.id, event.name.trim(), settings), selectedId = event.id)
        }
        PresetEvent.Reset -> copy(settings = selected.settings.copy(startPercent = settings.startPercent))
        is PresetEvent.Delete -> {
            require(event.id != ChargingRepositoryImpl.CUSTOM_ID) { "The Custom preset cannot be deleted" }
            require(presets.any { it.id == event.id }) { "Unknown preset: ${event.id}" }
            val next = copy(presets = presets.filterNot { it.id == event.id })
            if (selectedId == event.id) next.reduce(PresetEvent.Select(ChargingRepositoryImpl.CUSTOM_ID)) else next
        }
    }
}

sealed interface PresetEvent {
    data class Select(val id: String) : PresetEvent
    data class Edit(val settings: ChargingSettings) : PresetEvent
    data object Update : PresetEvent
    data class SaveNew(val id: String, val name: String) : PresetEvent
    data object Reset : PresetEvent
    data class Delete(val id: String) : PresetEvent
}

internal fun ChargingSettings.matchesPreset(other: ChargingSettings): Boolean =
    batteryCapacity == other.batteryCapacity && chargingPower == other.chargingPower &&
        maxPercent == other.maxPercent && availablePowers == other.availablePowers

internal fun ChargingSettings.encodeDraft(): String =
    (listOf(batteryCapacity, chargingPower, maxPercent) + availablePowers).joinToString(",")

internal fun decodePresetDraft(values: String): ChargingSettings? {
    val numbers = values.split(",").map { it.toFloatOrNull() ?: return null }
    if (numbers.size < 4 || numbers.any { !it.isFinite() } ||
        numbers[0] <= 0f || numbers[1] <= 0f || numbers[2] !in 0f..100f ||
        numbers.drop(3).any { it <= 0f }) return null
    return ChargingSettings.DEFAULT.copy(
        batteryCapacity = numbers[0], chargingPower = numbers[1], maxPercent = numbers[2],
        availablePowers = numbers.drop(3)
    )
}
