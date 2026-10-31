package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import info.anodsplace.viewmodel.BaseFlowViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class RunningViewState(
    val isLoading: Boolean = true,
    val startTime: Long = 0L,
    val estimatedEndTime: Long = 0L,
    val estimatedEndTimeText: String = "",
    val settings: ChargingSettings = ChargingSettings(),
    val calculation: ChargingCalculation = ChargingCalculation(),
    val elapsedMinutes: Int = 0
)

sealed interface RunningViewAction

sealed interface RunningViewEvent {
    data object StopCharging : RunningViewEvent
    data object UpdateCalculation : RunningViewEvent
}

class RunningViewModel(
    repository: ChargingRepository,
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
                    elapsedMinutes = status.elapsedMinutes
                )
            }.collect { viewState = it }
        }
    }

    override fun handleEvent(event: RunningViewEvent) {
        if (viewState.isLoading) return
        when (event) {
            RunningViewEvent.StopCharging -> viewModelScope.launch { service.stop() }
            RunningViewEvent.UpdateCalculation -> service.refresh()
        }
    }
}
