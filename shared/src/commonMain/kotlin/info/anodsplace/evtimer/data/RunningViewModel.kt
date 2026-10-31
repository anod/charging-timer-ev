package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import info.anodsplace.viewmodel.BaseFlowViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

class RunningViewModel(
    private val repository: ChargingRepository,
    private val service: ChargingService,
    private val clockTimeFormatter: ClockTimeFormatter
) : BaseFlowViewModel<RunningViewState, RunningViewEvent, RunningViewAction>() {
    init {
        viewState = RunningViewState()
        viewModelScope.launch {
            combine(repository.observeSettings(), service.status) { settings, status ->
                if (status.isLoading) return@combine RunningViewState()
                val sessionSettings = status.snapshot?.let {
                    settings.copy(
                        batteryCapacity = it.batteryCapacity,
                        startPercent = it.startPercent,
                        maxPercent = it.targetPercent,
                        chargingPower = status.calculation.chargingSpeed
                    )
                } ?: settings
                RunningViewState(
                    isLoading = false,
                    startTime = status.startTime,
                    estimatedEndTime = status.estimatedEndTime,
                    estimatedEndTimeText = if (status.estimatedEndTime > 0L) {
                        clockTimeFormatter.format(status.estimatedEndTime)
                    } else "",
                    settings = sessionSettings,
                    calculation = status.calculation,
                    elapsedMinutes = status.elapsedMinutes,
                    milestoneEstimate = status.milestoneEstimate,
                    milestoneEtaText = status.milestoneEstimate?.let {
                        clockTimeFormatter.format(it.estimatedTime)
                    }.orEmpty(),
                    startTimeText = status.snapshot?.let {
                        clockTimeFormatter.format(it.startTime)
                    }.orEmpty(),
                    timeProgress = status.snapshot?.let {
                        timeProgress(it, status.evaluatedAt, status.estimatedEndTime)
                    }
                )
            }.collect { viewState = it }
        }
    }

    override fun handleEvent(event: RunningViewEvent) {
        if (viewState.isLoading) return
        when (event) {
            RunningViewEvent.StopCharging -> viewModelScope.launch { service.stop() }
            RunningViewEvent.UpdateCalculation -> service.refresh()
            is RunningViewEvent.ChangePower -> viewModelScope.launch { service.changePower(event.powerKw) }
            is RunningViewEvent.AddPower -> viewModelScope.launch { addPower(event.powerKw) }
        }
    }

    // Saves the option without selecting it, so only the running session changes speed.
    private suspend fun addPower(powerKw: Float) {
        if (!powerKw.isFinite() || powerKw <= 0f) return
        val saved = repository.observeSettings().first()
        repository.saveSettings(saved.copy(availablePowers = (saved.availablePowers + powerKw).distinct().sorted()))
    }
}
