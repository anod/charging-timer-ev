package info.anodsplace.evtimer.data

import kotlin.math.ceil

const val CUSTOM_PRESET_ID = "custom"

data class ConfigurationViewState(
    val isLoading: Boolean = true,
    val settings: ChargingSettings = ChargingSettings(),
    val presets: List<ChargingPreset> = listOf(ChargingPreset(CUSTOM_PRESET_ID, "Default", ChargingSettings.DEFAULT)),
    val selectedPresetId: String = CUSTOM_PRESET_ID,
    val loadFailed: Boolean = false
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

data class TimePrediction(val minutesAhead: Double, val position: Float, val percent: Float)
data class TimeProgress(
    val fraction: Float,
    val predictions: List<TimePrediction>,
    val coarserPredictions: List<List<TimePrediction>> = emptyList()
)

fun timeProgress(snapshot: ChargingSessionSnapshot, now: Long, estimatedEndTime: Long): TimeProgress {
    val total = estimatedEndTime - snapshot.startTime
    if (total <= 0L) return TimeProgress(1f, emptyList())
    val fraction = ((now - snapshot.startTime).toDouble() / total).toFloat().coerceIn(0f, 1f)
    val origin = maxOf(now, snapshot.startTime)
    val remaining = (estimatedEndTime - origin).coerceAtLeast(0L) / 60_000.0
    if (remaining < 1.0) return TimeProgress(fraction, emptyList())

    val intervals = mutableListOf(5.0, 10.0, 15.0, 20.0, 30.0, 60.0,
        120.0, 180.0, 240.0, 360.0, 480.0, 720.0, 1440.0)
    while (intervals.last() < remaining) intervals += intervals.last() * 2.0
    val endpoint = TimePrediction(remaining, 1f, snapshot.targetPercent)
    val groups = intervals.filter { it >= remaining / 4.0 && it < remaining }.map { interval ->
        // Keep the exact endpoint, not a nearly adjacent regular tick (for example 180m and 181m).
        (1 until ceil(remaining / interval).toInt()).filter { index ->
            remaining - index * interval >= maxOf(interval * 0.1, 2.5)
        }.map { index ->
            val minutes = index * interval
            TimePrediction(
                minutesAhead = minutes,
                position = ((origin - snapshot.startTime + minutes * 60_000.0) / total).toFloat().coerceIn(0f, 1f),
                percent = ChargingCalculator.predict(snapshot, origin, minutes)
            )
        } + endpoint
    }.filter { it.size <= 4 } + listOf(listOf(endpoint))
    return TimeProgress(fraction, groups.first(), groups.drop(1))
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
    val timeProgress: TimeProgress? = null,
    val loadFailed: Boolean = false
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
