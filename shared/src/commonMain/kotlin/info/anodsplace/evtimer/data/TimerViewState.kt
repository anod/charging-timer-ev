package info.anodsplace.evtimer.data

const val CUSTOM_PRESET_ID = "custom"

data class ConfigurationViewState(
    val isLoading: Boolean = true,
    val settings: ChargingSettings = ChargingSettings(),
    val presets: List<ChargingPreset> = listOf(ChargingPreset(CUSTOM_PRESET_ID, "Default", ChargingSettings.DEFAULT)),
    val selectedPresetId: String = CUSTOM_PRESET_ID
) {
    val presetConfiguration get() = PresetConfiguration(presets, selectedPresetId, settings)
}

sealed interface ConfigurationViewAction {
    data object OpenRunning : ConfigurationViewAction
}

sealed interface ConfigurationViewEvent {
    data object StartCharging : ConfigurationViewEvent
    data class UpdateBatteryCapacity(val capacity: Float) : ConfigurationViewEvent
    data class UpdateChargingPower(val power: Float) : ConfigurationViewEvent
    data class AddCustomPower(val power: Float) : ConfigurationViewEvent
    data class RemoveCustomPower(val power: Float) : ConfigurationViewEvent
    data class UpdateStartPercent(val percent: Float) : ConfigurationViewEvent
    data class UpdateMaxPercent(val percent: Float) : ConfigurationViewEvent
    data class SelectPreset(val id: String) : ConfigurationViewEvent
    data object UpdatePreset : ConfigurationViewEvent
    data class SaveAsPreset(val name: String) : ConfigurationViewEvent
    data object ResetPreset : ConfigurationViewEvent
}

data class TimePrediction(val hoursAhead: Int, val position: Float, val percent: Float)
data class TimeProgress(val fraction: Float, val predictions: List<TimePrediction>)

fun timeProgress(snapshot: ChargingSessionSnapshot, now: Long, estimatedEndTime: Long): TimeProgress {
    val total = estimatedEndTime - snapshot.startTime
    if (total <= 0L) return TimeProgress(1f, emptyList())
    return TimeProgress(
        fraction = ((now - snapshot.startTime).toDouble() / total).toFloat().coerceIn(0f, 1f),
        predictions = listOf(1, 2).mapNotNull { hours ->
            val predictedTime = now + hours * 3_600_000L
            if (predictedTime > estimatedEndTime) null else TimePrediction(
                hoursAhead = hours,
                position = ((predictedTime - snapshot.startTime).toDouble() / total).toFloat().coerceIn(0f, 1f),
                percent = ChargingCalculator.predict(snapshot, now, hours * 60.0)
            )
        }
    )
}

data class RunningViewState(
    val isLoading: Boolean = true,
    val startTime: Long = 0L,
    val estimatedEndTime: Long = 0L,
    val estimatedEndTimeText: String = "",
    val settings: ChargingSettings = ChargingSettings(),
    val calculation: ChargingCalculation = ChargingCalculation(),
    val elapsedMinutes: Int = 0,
    val milestoneEstimate: MilestoneEstimate? = null,
    val milestoneEtaText: String = "",
    val startTimeText: String = "",
    val timeProgress: TimeProgress? = null
) {
    val isFinished: Boolean
        get() = !isLoading && estimatedEndTime > 0L &&
            calculation.timeRemainingMinutes == 0 && calculation.estimatedPercent >= settings.maxPercent
}

sealed interface RunningViewAction

sealed interface RunningViewEvent {
    data object StopCharging : RunningViewEvent
    data object UpdateCalculation : RunningViewEvent
    data class ChangePower(val powerKw: Float) : RunningViewEvent
    data class AddPower(val powerKw: Float) : RunningViewEvent
}
