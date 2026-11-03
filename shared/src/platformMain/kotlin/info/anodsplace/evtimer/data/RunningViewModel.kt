package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import info.anodsplace.viewmodel.BaseFlowViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RunningViewModel(
    private val repository: ChargingRepository,
    private val service: ChargingService,
    private val clockTimeFormatter: ClockTimeFormatter
) : BaseFlowViewModel<RunningViewState, RunningViewEvent, RunningViewAction>() {
    init {
        viewState = RunningViewState()
        viewModelScope.launch {
            var lastRunningState: RunningViewState? = null
            combine(repository.observeSettings(), service.status) { settings, status ->
                if (status.isLoading) return@combine RunningViewState()
                if (!status.isRunning) lastRunningState?.let { return@combine it }
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
                ).also { if (status.isRunning) lastRunningState = it }
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
